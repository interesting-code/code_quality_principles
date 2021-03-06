package ru.job4j.principle_004;

import org.apache.commons.dbcp.BasicDataSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Refactoring store.
 */
public class DbStore implements Store<User> {
    /**
     * Db source.
     */
    private final BasicDataSource source;

    /**
     * Dispatch param for prepared statement.
     */
    private final Map<Class<?>, TripleConEx<Integer, PreparedStatement, Object>> dispatch = new HashMap<>();

    /**
     * Constructor.
     * @param source DB source.
     */
    public DbStore(BasicDataSource source) {
        this.source = source;
        this.dispatch.put(Integer.class, (index, ps, value) ->  ps.setInt(index, (Integer) value));
        this.dispatch.put(String.class, (index, ps, value) ->  ps.setString(index, (String) value));
    }

    /**
     * For-each statement with index.
     * @param list list.
     * @param consumer consumer.
     * @param <T> type.
     * @throws Exception possible exception.
     */
    private <T> void forIndex(List<T> list, BiConEx<Integer, T> consumer) throws Exception {
        for (int index = 0; index != list.size(); index++) {
            consumer.accept(index, list.get(index));
        }
    }

    /**
     * Wrapper with prepared statement.
     * @param sql query
     * @param params params
     * @param fun function with prepared statement.
     * @param key for generating key.
     * @param <R> type
     * @return value.
     */
    private <R> Optional<R> db(String sql, List<Object> params, FunEx<PreparedStatement, R> fun, int key) {
        Optional<R> rst = Optional.empty();
        try (var connection = this.source.getConnection();
             var pr = connection.prepareStatement(sql, key)) {
            this.forIndex(
                    params,
                    (index, value) -> dispatch.get(value.getClass()).accept(index + 1, pr, value)
            );
            rst = Optional.of(fun.apply(pr));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rst;
    }

    /**
     * Overload without key.
     * @param sql query.
     * @param params params.
     * @param fun function with prepared statement.
     * @param <R> type.
     * @return value.
     */
    private <R> Optional<R> db(String sql, List<Object> params, FunEx<PreparedStatement, R> fun) {
        return this.db(sql, params, fun, Statement.NO_GENERATED_KEYS);
    }

    /**
     * The same like DB only without return value.
     * @param sql query.
     * @param params params.
     * @param fun function with prepared statement.
     * @param key generated key.
     * @param <R> type.
     */
    private <R> void db(String sql, List<Object> params, ConEx<PreparedStatement> fun, int key) {
        this.db(
                sql, params,
                ps -> {
                    fun.accept(ps);
                    return Optional.empty();
                }, key
        );
    }

    /**
     * The same like DB only without return value.
     * @param sql query.
     * @param params params.
     * @param fun funcation with prepared statement.
     * @param <R> type.
     */
    private <R> void db(String sql, List<Object> params, ConEx<PreparedStatement> fun) {
        this.db(sql, params, fun, Statement.NO_GENERATED_KEYS);
    }

    @Override
    public User add(User user) {
        this.db(
                "insert into users (login) values (?)", List.of(user.getLogin()),
                ps -> {
                    ps.executeUpdate();
                    try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            user.setId(generatedKeys.getInt(1));
                        }
                    }
                },
                Statement.RETURN_GENERATED_KEYS
        );
        return user;
    }

    @Override
    public List<User> findAll() {
        final List<User> users = new ArrayList<>();
        this.db(
                "select * from users", List.of(),
                ps -> {
                    try (final ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            users.add(new User(rs.getInt("id"), rs.getString("login")));
                        }
                    }
                }
        );
        return users;
    }

    @Override
    public void update(User model) {
        this.db(
                "update users set login=? where id=?",
                List.of(model.getLogin(), model.getId()),
                ps -> {
                    ps.executeUpdate();
                }
        );
    }

    @Override
    public void delete(int id) {
        this.db(
                "delete users where id=?", List.of(id),
                ps -> {
                    ps.executeUpdate();
                }
        );
    }

    @Override
    public User findById(int id) {
        return this.db(
                "select * from users where id=?", List.of(id),
                ps -> {
                    User rsl = new User();
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            rsl = new User(rs.getInt("id"), rs.getString("login"));
                        }
                    }
                    return rsl;
                }
        ).orElse(new User());
    }
}
