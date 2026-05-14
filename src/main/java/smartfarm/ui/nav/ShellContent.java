package smartfarm.ui.nav;

/**
 * Type-safe identifiers for the inner content panes rendered inside
 * {@code SHELL_VIEW}.
 *
 * <p>These are NOT Gluon Views — they are content slots that B2's
 * {@code ShellController} will wire to FXML pages via content swap.
 *
 * <p>B1 only declares the names; no code consumes this enum yet.
 */
public enum ShellContent {
    HOME,
    CROPS,
    PLOTS,
    WORKERS,
    TASKS,
    ALERTS,
    MONITORING,
    HARVEST,
    REPORTS,
    LOGS,
    DISEASE,
    ATTENDANCE,
    ABOUT,
    SETTINGS
}
