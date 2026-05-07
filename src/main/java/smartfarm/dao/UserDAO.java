package smartfarm.dao;

/**
 * @deprecated The unified `users` table has been split into `admin`, `manager`, `worker`.
 * Use {@link AdminDAO}, {@link ManagerDAO}, or {@link WorkerDAO} instead.
 *
 * This class is intentionally left empty as a placeholder for any leftover
 * imports during migration; new code must use the role-specific DAOs.
 */
@Deprecated
public final class UserDAO {
    private UserDAO() {}
}