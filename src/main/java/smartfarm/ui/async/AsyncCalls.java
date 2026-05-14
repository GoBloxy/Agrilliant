package smartfarm.ui.async;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.Node;

import smartfarm.util.DBConnection;
import smartfarm.util.Logger;

/**
 * Thin helper around {@link DBConnection#runAsync(Callable)} that takes care
 * of the two common boilerplate steps a controller would otherwise repeat at
 * every call site:
 *
 * <ol>
 *   <li>Marshal the result (or the exception) back onto the JavaFX
 *       Application Thread via {@link Platform#runLater(Runnable)} before
 *       handing it to the supplied UI action.</li>
 *   <li>Unwrap {@link CompletionException} — DBConnection.runAsync wraps
 *       any DAO RuntimeException as a {@code CompletionException} cause,
 *       which is rarely the actual error the caller wants to log.</li>
 * </ol>
 *
 * <h2>Typical call site</h2>
 *
 * <p>Before (synchronous, blocks the FX thread on every DB hit):
 * <pre>{@code
 *   List<Crop> all = cropDao.findAll();
 *   populateCropTable(all);
 * }</pre>
 *
 * <p>After (FX thread stays free during the JDBC round-trip):
 * <pre>{@code
 *   AsyncCalls.runAndApply(
 *       () -> cropDao.findAll(),
 *       this::populateCropTable);
 * }</pre>
 *
 * <h2>Threading guarantees</h2>
 *
 * <ul>
 *   <li>{@code dbWork} runs on Hagag's single-threaded
 *       {@code agrilliant-db} executor (see
 *       {@link DBConnection#runAsync(Callable)}). All DB work is
 *       serialized — safe to use the shared {@link
 *       java.sql.Connection}.</li>
 *   <li>{@code uiAction} and {@code onError} are guaranteed to run on
 *       the JavaFX Application Thread.</li>
 *   <li>If the FX toolkit hasn't been initialized yet (e.g. during
 *       headless tests), the callbacks still execute but the runLater
 *       behaviour depends on the host. Don't call AsyncCalls from
 *       static initializers.</li>
 * </ul>
 *
 * <h2>Cancellation</h2>
 *
 * <p>The returned {@link CompletableFuture} is forwarded straight from
 * {@link DBConnection#runAsync(Callable)}. Cancelling it does <b>not</b>
 * interrupt the underlying JDBC call (the executor's task already
 * captured the Callable), but it does prevent the {@code uiAction} from
 * firing if the cancel beats the DB completion. Use this pattern only
 * for stale-result suppression (e.g. user typed a new search query):
 *
 * <pre>{@code
 *   if (lastFuture != null) lastFuture.cancel(false);
 *   lastFuture = AsyncCalls.runAndApply(...);
 * }</pre>
 *
 * <h2>Lane</h2>
 *
 * <p>Lives in {@code smartfarm.ui.async} (3bdelbary's UI lane). Imports
 * from {@code smartfarm.util.DBConnection} (Hagag H4) and
 * {@code smartfarm.util.Logger} (Hagag H5/H10) only — both are stable
 * read-only utilities per the migration doc §11.5.
 *
 * @since Phase 2 (P2.1)
 */
public final class AsyncCalls {

    private static final String TAG = "AsyncCalls";

    private AsyncCalls() {
        // utility class
    }

    // ─── Core: run DB work, apply result on FX thread ───────────────────

    /**
     * Run {@code dbWork} on the DB executor, then apply the result via
     * {@code uiAction} on the FX thread. Errors are logged via
     * {@link Logger#e(String, String, Throwable)} and otherwise swallowed.
     *
     * @param dbWork   the DAO call (blocking JDBC, runs off-FX-thread)
     * @param uiAction what to do with the result (runs on FX thread)
     * @param <T>      result type
     * @return the underlying future so callers can chain / cancel
     */
    public static <T> CompletableFuture<T> runAndApply(
            Callable<T> dbWork,
            Consumer<T> uiAction) {
        return runAndApply(dbWork, uiAction, AsyncCalls::defaultErrorHandler);
    }

    /**
     * Same as {@link #runAndApply(Callable, Consumer)} but with an explicit
     * error handler. Use when the caller wants to show a user-visible
     * dialog / inline error label instead of (or in addition to) logging.
     */
    public static <T> CompletableFuture<T> runAndApply(
            Callable<T> dbWork,
            Consumer<T> uiAction,
            Consumer<Throwable> onError) {
        return DBConnection.runAsync(dbWork).whenComplete((result, throwable) -> {
            if (throwable != null) {
                final Throwable cause = unwrap(throwable);
                Platform.runLater(() -> onError.accept(cause));
            } else {
                Platform.runLater(() -> uiAction.accept(result));
            }
        });
    }

    /**
     * Timeout overload of
     * {@link #runAndApply(Callable, Consumer, Consumer)}. If the DB work
     * has not completed within {@code timeout}, the chain completes with a
     * {@link java.util.concurrent.TimeoutException} delivered to
     * {@code onError} on the FX thread.
     *
     * <p><b>Caveat:</b> CompletableFuture.orTimeout does <i>not</i> cancel
     * the underlying JDBC call — the DB executor's worker remains blocked
     * inside the driver until the connection itself returns or fails. The
     * timeout only frees the FX side so the UI can react (e.g. SplashView
     * can fall through to the sign-in screen on a hung restore).
     */
    public static <T> CompletableFuture<T> runAndApply(
            Callable<T> dbWork,
            Consumer<T> uiAction,
            Consumer<Throwable> onError,
            Duration timeout) {
        return DBConnection.runAsync(dbWork)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        final Throwable cause = unwrap(throwable);
                        Platform.runLater(() -> onError.accept(cause));
                    } else {
                        Platform.runLater(() -> uiAction.accept(result));
                    }
                });
    }

    // ─── Convenience: busy-state UI gating ──────────────────────────────

    /**
     * Variant of {@link #runAndApply(Callable, Consumer)} that disables
     * {@code busyNode} for the duration of the call and re-enables it
     * before the success or error handler runs. Typical usage is to pass
     * the button that triggered the action (e.g. "Save", "Refresh") so
     * the user can't double-click while the DB work is in flight.
     *
     * <p>If {@code busyNode} is {@code null} this method behaves
     * identically to {@link #runAndApply(Callable, Consumer)}.
     */
    public static <T> CompletableFuture<T> runWithBusy(
            Node busyNode,
            Callable<T> dbWork,
            Consumer<T> uiAction) {
        return runWithBusy(busyNode, dbWork, uiAction, AsyncCalls::defaultErrorHandler);
    }

    /**
     * Full-control overload of {@link #runWithBusy(Node, Callable, Consumer)}.
     */
    public static <T> CompletableFuture<T> runWithBusy(
            Node busyNode,
            Callable<T> dbWork,
            Consumer<T> uiAction,
            Consumer<Throwable> onError) {
        if (busyNode != null) {
            busyNode.setDisable(true);
        }
        return runAndApply(
                dbWork,
                result -> {
                    if (busyNode != null) {
                        busyNode.setDisable(false);
                    }
                    uiAction.accept(result);
                },
                err -> {
                    if (busyNode != null) {
                        busyNode.setDisable(false);
                    }
                    onError.accept(err);
                });
    }

    /**
     * Timeout overload of
     * {@link #runWithBusy(Node, Callable, Consumer, Consumer)}. Useful for
     * sign-in / sign-up buttons so the UI doesn't stay disabled forever if
     * the DB hangs. The {@code onError} consumer receives a
     * {@link java.util.concurrent.TimeoutException} on timeout. See the
     * caveat on
     * {@link #runAndApply(Callable, Consumer, Consumer, Duration)} about
     * the underlying JDBC call not being cancelled.
     */
    public static <T> CompletableFuture<T> runWithBusy(
            Node busyNode,
            Callable<T> dbWork,
            Consumer<T> uiAction,
            Consumer<Throwable> onError,
            Duration timeout) {
        if (busyNode != null) {
            busyNode.setDisable(true);
        }
        return runAndApply(
                dbWork,
                result -> {
                    if (busyNode != null) {
                        busyNode.setDisable(false);
                    }
                    uiAction.accept(result);
                },
                err -> {
                    if (busyNode != null) {
                        busyNode.setDisable(false);
                    }
                    onError.accept(err);
                },
                timeout);
    }

    // ─── Fire-and-forget writes (UPDATE / INSERT / DELETE) ──────────────

    /**
     * Run a write-only DB action with no UI follow-up on success. Errors
     * are still logged + reported via the default handler so failed
     * UPDATEs don't disappear silently. Returns a future the caller can
     * chain if a follow-up is wanted (e.g. "after save, refresh list").
     *
     * <pre>{@code
     *   AsyncCalls.runFireAndForget(() -> cropDao.update(crop))
     *             .thenRun(this::refreshTable);
     * }</pre>
     */
    public static CompletableFuture<Void> runFireAndForget(Runnable dbWork) {
        return DBConnection.runAsync(() -> {
            dbWork.run();
            return null;
        }).whenComplete((v, throwable) -> {
            if (throwable != null) {
                final Throwable cause = unwrap(throwable);
                Platform.runLater(() -> defaultErrorHandler(cause));
            }
        }).thenApply(x -> null);
    }

    /**
     * Variant of {@link #runFireAndForget(Runnable)} that runs a follow-up
     * UI action on the FX thread after successful completion.
     *
     * <pre>{@code
     *   AsyncCalls.runFireAndForgetThen(
     *       () -> cropDao.delete(id),
     *       () -> refreshTable());
     * }</pre>
     */
    public static CompletableFuture<Void> runFireAndForgetThen(
            Runnable dbWork,
            Runnable onSuccess) {
        return runFireAndForgetThen(dbWork, onSuccess, AsyncCalls::defaultErrorHandler);
    }

    /**
     * Full-control overload of {@link #runFireAndForgetThen(Runnable, Runnable)}.
     */
    public static CompletableFuture<Void> runFireAndForgetThen(
            Runnable dbWork,
            Runnable onSuccess,
            Consumer<Throwable> onError) {
        return DBConnection.runAsync(() -> {
            dbWork.run();
            return null;
        }).whenComplete((v, throwable) -> {
            if (throwable != null) {
                final Throwable cause = unwrap(throwable);
                Platform.runLater(() -> onError.accept(cause));
            } else {
                Platform.runLater(onSuccess);
            }
        }).thenApply(x -> null);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /**
     * Unwrap the outer {@link CompletionException} that
     * {@link CompletableFuture#whenComplete} hands to its callback,
     * exposing the underlying DAO exception that the caller actually
     * cares about.
     */
    private static Throwable unwrap(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    /**
     * Default error sink — logs at ERROR level and otherwise swallows.
     * Controllers that need user-visible error reporting (toast, inline
     * label, dialog) should pass an explicit {@code onError} consumer
     * instead of relying on this fallback.
     */
    private static void defaultErrorHandler(Throwable t) {
        Logger.e(TAG, "Background DB work failed: " + t.getMessage(), t);
    }
}
