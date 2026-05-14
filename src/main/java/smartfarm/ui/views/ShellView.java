package smartfarm.ui.views;

import com.gluonhq.attach.lifecycle.LifecycleEvent;
import com.gluonhq.attach.lifecycle.LifecycleService;
import com.gluonhq.attach.util.Services;
import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.NavigationDrawer;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;
import smartfarm.model.User;
import smartfarm.service.LiveSensorData;
import smartfarm.service.SessionManager;
import smartfarm.ui.DashboardController;
import smartfarm.ui.DashboardController.NavTarget;
import smartfarm.ui.DashboardController.SidebarStatus;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.nav.NavContext;
import smartfarm.util.Logger;

import java.io.IOException;

/**
 * Post-login shell: wraps the existing {@code dashboard.fxml} and
 * decorates it with mobile-native chrome — a Gluon {@link AppBar}
 * (top, with hamburger) and a {@link NavigationDrawer} (slide-in
 * from the left).
 *
 * <p>The sidebar and topbar inside {@code dashboard.fxml} are still
 * present (so {@link DashboardController}'s legacy logic — datetime,
 * weather, status dots, user pill — keeps running for the desktop
 * look) but are hidden ({@code visible=false managed=false}). On
 * mobile they take zero space and the Gluon chrome above is what
 * the user sees.
 *
 * <p>Drawer items dispatch through
 * {@link DashboardController#navigate(NavTarget)} so the inner
 * {@code pageContainer} swap logic is shared 1:1 with the
 * (now-hidden) sidebar buttons.
 *
 * <p>The current user is read from {@link NavContext} on every
 * {@code onShowing} and pushed into
 * {@link DashboardController#setCurrentUser(User)} so the dashboard
 * refreshes if the user switches via a sign-out / sign-in cycle.
 */
public class ShellView extends View {

    private static final String TAG = "ShellView";

    private DashboardController controller;

    /**
     * Set up flag — drawer items + headers are configured once per
     * JVM. The drawer reference itself is owned by AppManager and is
     * shared, so re-populating on every showing would duplicate items.
     */
    private boolean drawerConfigured = false;

    /** Set up flag — Gluon Attach LifecycleService PAUSE/RESUME hooks
     *  are wired once per controller instance, not on every showing. */
    private boolean lifecycleServiceWired = false;
    private Label drawerSystemStatus;
    private Label drawerDbStatus;
    private Label drawerSensorStatus;
    private Circle drawerDotSystem;
    private Circle drawerDotDb;
    private Circle drawerDotSensors;
    private ChangeListener<Number> drawerSensorStatusListener;

    /**
     * Viewport-width threshold above which the legacy sidebar inflates
     * inline and the Gluon AppBar hamburger is hidden. Set to a very
     * high value (9999) so the sidebar never inflates — the app is
     * always mobile-first with the NavigationDrawer + hamburger pattern.
     */
    private static final double WIDE_BREAKPOINT = 9999.0;

    /**
     * P2.5: scene-width listener handle, retained so we can detach on
     * setOnHiding. Attached lazily from setOnShowing once the view has a
     * Scene. Held null while ShellView is hidden, which is why the
     * applyResponsiveChrome / AppBar mutations are safe — the listener
     * never fires for the wrong view.
     */
    private ChangeListener<Number> widthListener;

    public ShellView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent content = loader.load();
            this.controller = loader.getController();
            setCenter(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dashboard.fxml", e);
        }

        setOnShowing(e -> {
            Logger.d(TAG, "showing");
            configureAppBar();
            configureNavigationDrawerOnce();
            wireLifecycleServiceOnce();
            pushCurrentUser();
            // B9: re-attach the dashboard's clock + LiveSensorData listeners.
            // Idempotent — first call is a near-no-op because initialize()
            // already started them; later calls (post-logout → re-login,
            // or LifecycleService RESUME) restart what stopLifecycle paused.
            if (controller != null) {
                controller.startLifecycle();
            }
            attachDrawerStatusListener();
            refreshDrawerFooterStatus();
            // P2.5: width-driven sidebar + hamburger toggling. Attached here
            // so it only fires while ShellView is the active view; detached
            // in setOnHiding to keep chrome from bleeding into sign-in /
            // sign-up.
            attachWidthListener();
        });
        setOnHiding(e -> {
            Logger.d(TAG, "hiding");
            // P2.5: detach the width listener before clearing the AppBar so a
            // width change during sign-in / sign-up can't re-add chrome we
            // just removed.
            detachWidthListener();
            // Sign-in / Sign-up don't want our hamburger or actions
            // bleeding into their (also AppBar-using) views.
            AppBar appBar = AppManager.getInstance().getAppBar();
            appBar.setNavIcon(null);
            appBar.getActionItems().clear();
            appBar.setVisible(false);
            detachDrawerStatusListener();
            // B9: stop the clock + detach LiveSensorData listeners while
            // the shell is off-screen (logout). Safe even if the user
            // re-enters via SHELL.switchTo() — startLifecycle above
            // re-attaches.
            if (controller != null) {
                controller.stopLifecycle();
            }
        });
    }

    // --------------------------------------------------------------
    // Gluon Attach LifecycleService — PAUSE/RESUME hand-off (B9 + B10).
    //
    // On Android, the OS suspends the JVM when the user switches away
    // from the app. PAUSE fires just before that; RESUME fires when the
    // app is foregrounded again. We use these to drive the dashboard's
    // clock + LiveSensorData listeners — the dashboard goes idle while
    // the app is in the background and comes back to life on resume.
    //
    // On desktop the service is a no-op (no-op service factory in
    // attach-lifecycle for the host platform), so the .ifPresent guard
    // simply skips the registration. No-op behaviour is correct for
    // desktop, where the user is never "backgrounded" the same way.
    //
    // The hook is wired once per ShellView instance; the same service
    // singleton is shared across the JVM and listener removal isn't
    // needed because the ShellView lives for the whole session.
    // --------------------------------------------------------------

    private void wireLifecycleServiceOnce() {
        if (lifecycleServiceWired) return;
        lifecycleServiceWired = true;

        Services.get(LifecycleService.class).ifPresent(svc -> {
            svc.addListener(LifecycleEvent.PAUSE, () -> {
                Logger.d(TAG, "LifecycleService PAUSE — stopping dashboard lifecycle");
                if (controller != null) {
                    controller.stopLifecycle();
                }
            });
            svc.addListener(LifecycleEvent.RESUME, () -> {
                Logger.d(TAG, "LifecycleService RESUME — restarting dashboard lifecycle");
                if (controller != null) {
                    controller.startLifecycle();
                }
            });
            Logger.i(TAG, "LifecycleService wired (PAUSE/RESUME → DashboardController)");
        });
    }

    // --------------------------------------------------------------
    // AppBar — refreshed every showing.
    // --------------------------------------------------------------

    private void configureAppBar() {
        AppBar appBar = AppManager.getInstance().getAppBar();
        appBar.setVisible(true);
        appBar.setTitleText("Agrilliant");

        // P2.5: hamburger is now toggled by applyResponsiveChrome based on
        // the current scene width — it's hidden on wide viewports where the
        // inline sidebar is visible. attachWidthListener() runs after this
        // method (both fire from setOnShowing) and applies the correct
        // chrome.

        // Trailing user chip + sign-out icon. Tapping the chip is a
        // no-op for now; long-press / tap-handler hooks can land in a
        // later commit (B9 lifecycle sweep).
        appBar.getActionItems().clear();
        User u = NavContext.get().getCurrentUser();
        if (u != null) {
            Label userLbl = new Label(u.getFullName() != null ? u.getFullName() : "User");
            userLbl.getStyleClass().add("appbar-user-label");
            appBar.getActionItems().add(userLbl);
        }
        Button signOut = MaterialDesignIcon.EXIT_TO_APP.button(ev -> signOut());
        appBar.getActionItems().add(signOut);
    }

    // --------------------------------------------------------------
    // NavigationDrawer — populated once.
    // --------------------------------------------------------------

    private void configureNavigationDrawerOnce() {
        if (drawerConfigured) return;
        drawerConfigured = true;

        NavigationDrawer drawer = AppManager.getInstance().getDrawer();
        drawer.setHeader(buildDrawerHeader());

        drawer.getItems().setAll(
                drawerItem("Dashboard",         "fth-grid",         NavTarget.DASHBOARD),
                drawerItem("Monitoring",        "fth-activity",     NavTarget.MONITORING),
                drawerItem("Disease Detection", "fth-search",       NavTarget.DISEASE),
                drawerItem("Alerts",            "fth-bell",         NavTarget.ALERTS),
                drawerItem("Crops",             "fth-feather",      NavTarget.CROPS),
                drawerItem("Plots",             "fth-map",          NavTarget.PLOTS),
                drawerItem("Workers",           "fth-users",        NavTarget.WORKERS),
                drawerItem("Attendance",        "fth-user-check",   NavTarget.ATTENDANCE),
                drawerItem("Tasks",             "fth-check-square", NavTarget.TASKS),
                drawerItem("Harvests",          "fth-package",      NavTarget.HARVESTS),
                drawerItem("Reports",           "fth-bar-chart-2",  NavTarget.REPORTS),
                drawerItem("Settings",          "fth-settings",     NavTarget.SETTINGS),
                drawerItem("Users",             "fth-user",         NavTarget.USERS),
                drawerItem("Logs",              "fth-file-text",    NavTarget.LOGS)
        );

        drawer.setFooter(buildDrawerFooter());
    }

    private Node buildDrawerHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 16, 16, 16));
        header.getStyleClass().add("drawer-header");

        HBox brandRow = new HBox(10);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon brandIcon = new FontIcon("fth-feather");
        brandIcon.setIconSize(22);
        brandIcon.getStyleClass().add("drawer-brand-icon");
        StackPane brandBadge = new StackPane(brandIcon);
        brandBadge.getStyleClass().add("drawer-brand-badge");
        Label brand = new Label("Agrilliant");
        brand.getStyleClass().add("drawer-title");
        brandRow.getChildren().addAll(brandBadge, brand);

        header.getChildren().add(brandRow);

        // User identity line — refreshed by replacing the header on
        // every showing, but for B3X.5 we just snapshot at first show.
        User u = NavContext.get().getCurrentUser();
        if (u != null) {
            VBox userBox = new VBox(0);
            Label nameLbl = new Label(u.getFullName() != null ? u.getFullName() : "Signed in");
            nameLbl.getStyleClass().add("drawer-user-name");
            Label roleLbl = new Label(u.getRole() != null ? u.getRole().name() : "User");
            roleLbl.getStyleClass().add("drawer-subtitle");
            userBox.getChildren().addAll(nameLbl, roleLbl);
            header.getChildren().add(userBox);
        }
        return header;
    }

    private Node buildDrawerFooter() {
        VBox footer = new VBox(6);
        footer.setPadding(new Insets(12, 16, 16, 16));
        footer.getStyleClass().add("drawer-footer");

        drawerDotSystem = new Circle(4);
        drawerDotDb = new Circle(4);
        drawerDotSensors = new Circle(4);
        drawerSystemStatus = new Label("Online");
        drawerDbStatus = new Label("Connected");
        drawerSensorStatus = new Label("0 Active");

        VBox statusCard = new VBox(6);
        statusCard.setPadding(new Insets(10));
        statusCard.getStyleClass().add("drawer-status-card");
        statusCard.getChildren().addAll(
                drawerStatusRow("System Status", drawerDotSystem, drawerSystemStatus),
                drawerStatusRow("Database", drawerDotDb, drawerDbStatus),
                drawerStatusRow("IoT Sensors", drawerDotSensors, drawerSensorStatus)
        );

        Button signOut = new Button("Sign Out", new FontIcon("fth-log-out"));
        signOut.setMaxWidth(Double.MAX_VALUE);
        signOut.getStyleClass().add("btn-secondary");
        signOut.setOnAction(ev -> signOut());

        Label version = new Label("Version 1.0.0");
        version.getStyleClass().add("drawer-subtitle");
        version.setMaxWidth(Double.MAX_VALUE);
        version.setAlignment(Pos.CENTER);

        footer.getChildren().addAll(statusCard, signOut, version);
        return footer;
    }

    private Node drawerStatusRow(String title, Circle dot, Label value) {
        dot.getStyleClass().setAll("status-dot-offline");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("drawer-status-title");
        value.getStyleClass().setAll("drawer-status-label", "drawer-status-offline");
        VBox text = new VBox(0, titleLabel, value);
        HBox row = new HBox(8, dot, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void attachDrawerStatusListener() {
        if (drawerSensorStatusListener != null) return;
        drawerSensorStatusListener = (obs, oldVal, newVal) -> refreshDrawerFooterStatus();
        LiveSensorData.getInstance().activeSensorsProperty().addListener(drawerSensorStatusListener);
    }

    private void detachDrawerStatusListener() {
        if (drawerSensorStatusListener == null) return;
        LiveSensorData.getInstance().activeSensorsProperty().removeListener(drawerSensorStatusListener);
        drawerSensorStatusListener = null;
    }

    private void refreshDrawerFooterStatus() {
        if (controller == null || drawerSystemStatus == null) return;
        SidebarStatus status = controller.getSidebarStatus();
        applyDrawerStatus(drawerDotSystem, drawerSystemStatus, status.system(), status.systemOnline());
        applyDrawerStatus(drawerDotDb, drawerDbStatus, status.database(), status.databaseOnline());
        applyDrawerStatus(drawerDotSensors, drawerSensorStatus, status.sensors(), status.sensorsOnline());
    }

    private void applyDrawerStatus(Circle dot, Label label, String text, boolean online) {
        dot.getStyleClass().setAll(online ? "status-dot-online" : "status-dot-offline");
        label.setText(text);
        label.getStyleClass().setAll("drawer-status-label",
                online ? "drawer-status-online" : "drawer-status-offline");
    }

    private NavigationDrawer.Item drawerItem(String title, String iconLiteral, NavTarget target) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(18);
        NavigationDrawer.Item item = new NavigationDrawer.Item(title, icon);
        item.setOnMouseClicked(ev -> {
            if (controller != null) {
                controller.navigate(target);
            }
            AppManager.getInstance().getDrawer().close();
        });
        return item;
    }

    // --------------------------------------------------------------
    // P2.5 — responsive sidebar + hamburger.
    //
    // The legacy sidebar inside dashboard.fxml is hidden by default so the
    // mobile-native AppBar + NavigationDrawer drives the layout. On wide
    // viewports (>= WIDE_BREAKPOINT) we inflate the sidebar inline and hide
    // the AppBar hamburger — the user gets a desktop look without losing
    // the drawer-based nav available below the breakpoint.
    //
    // The width listener is attached only while ShellView is the active
    // view (setOnShowing → attach, setOnHiding → detach) so a resize during
    // sign-in / sign-up can't reach into our AppBar.
    // --------------------------------------------------------------

    private void attachWidthListener() {
        if (widthListener != null) return;
        Scene scene = getScene();
        if (scene == null) return;
        widthListener = (obs, oldW, newW) -> applyResponsiveChrome(newW.doubleValue());
        scene.widthProperty().addListener(widthListener);
        // Apply once up-front so the chrome matches the current width
        // before the first user interaction.
        applyResponsiveChrome(scene.getWidth());
    }

    private void detachWidthListener() {
        if (widthListener == null) return;
        Scene scene = getScene();
        if (scene != null) {
            scene.widthProperty().removeListener(widthListener);
        }
        widthListener = null;
    }

    private void applyResponsiveChrome(double width) {
        boolean wide = width >= WIDE_BREAKPOINT;

        if (controller != null) {
            controller.setSidebarInline(wide);
        }

        // Hamburger only when narrow — on wide viewports the inline sidebar
        // is the nav surface and the hamburger would be redundant.
        AppBar appBar = AppManager.getInstance().getAppBar();
        if (wide) {
            appBar.setNavIcon(null);
        } else {
            appBar.setNavIcon(MaterialDesignIcon.MENU.button(
                    ev -> AppManager.getInstance().getDrawer().open()));
        }
    }

    // --------------------------------------------------------------
    // Misc.
    // --------------------------------------------------------------

    private void pushCurrentUser() {
        if (controller == null) return;
        User u = NavContext.get().getCurrentUser();
        if (u != null) {
            controller.setCurrentUser(u);
        } else {
            Logger.w(TAG, "Shell shown with no current user — DashboardController will render with null user");
        }
    }

    private void signOut() {
        Logger.d(TAG, "sign-out");
        SessionManager.clearSession();
        NavContext.get().clear();
        AppView.SIGNIN.switchTo();
    }
}
