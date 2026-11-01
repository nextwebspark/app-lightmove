package app.lightmove.api;

import app.lightmove.api.core.tenant.WorkspaceScope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * For a test that calls the application's own beans directly rather than through a request: no principal is signed
 * in, so the call names the workspace it works for, as a scheduled job would — here, the workspace of the project or
 * person it is about, read from above row-level security.
 */
public final class TestWorkspaceScopes {

    private TestWorkspaceScopes() {
    }

    public static <T> T inProjectsWorkspace(JdbcTemplate fixture, UUID projectId, Supplier<T> work) {
        return WorkspaceScope.call(workspaceOfProject(fixture, projectId), work);
    }

    public static void inProjectsWorkspace(JdbcTemplate fixture, UUID projectId, Runnable work) {
        WorkspaceScope.run(workspaceOfProject(fixture, projectId), work);
    }

    public static UUID workspaceOfProject(JdbcTemplate fixture, UUID projectId) {
        return fixture.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?", UUID.class, projectId);
    }

    /** In the workspace of a row of a table carrying one — a test's own name, never a request's. */
    public static <T> T inWorkspaceOfRow(JdbcTemplate fixture, String table, UUID id, Supplier<T> work) {
        UUID workspaceId = fixture.queryForObject("SELECT workspace_id FROM " + table + " WHERE id = ?", UUID.class,
                id);
        return WorkspaceScope.call(workspaceId, work);
    }

    public static void inWorkspaceOfRow(JdbcTemplate fixture, String table, UUID id, Runnable work) {
        inWorkspaceOfRow(fixture, table, id, () -> {
            work.run();
            return null;
        });
    }

    /**
     * A test's own handle on a bean whose every call names its workspace — a parameter named {@code workspaceId}, else
     * its first {@code UUID} argument, or an argument answering {@code workspaceId()} — each call run in that workspace's
     * scope, as the request or job that makes it in the application would. Only the test's direct calls go through it;
     * the application's own wiring still gets the bean itself, so nothing the application forgot to scope is scoped
     * here.
     */
    @SuppressWarnings("unchecked")
    public static <T> T scopedByArguments(T bean) {
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> {
            UUID workspaceId = workspaceNamedBy(invocation.getMethod(), invocation.getArguments());
            if (workspaceId == null) {
                return invocation.proceed();
            }
            return WorkspaceScope.call(workspaceId, () -> {
                try {
                    return invocation.proceed();
                } catch (RuntimeException | Error unchecked) {
                    throw unchecked;
                } catch (Throwable checked) {
                    throw new IllegalStateException(checked);
                }
            });
        });
        return (T) factory.getProxy();
    }

    private static UUID workspaceNamedBy(Method method, Object[] arguments) {
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].getName().equals("workspaceId") && arguments[index] instanceof UUID id) {
                return id;
            }
        }
        for (Object argument : arguments) {
            if (argument instanceof UUID id) {
                return id;
            }
            UUID carried = workspaceIdOf(argument);
            if (carried != null) {
                return carried;
            }
        }
        return null;
    }

    private static UUID workspaceIdOf(Object argument) {
        if (argument == null) {
            return null;
        }
        try {
            Method accessor = argument.getClass().getMethod("workspaceId");
            return accessor.invoke(argument) instanceof UUID id ? id : null;
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
