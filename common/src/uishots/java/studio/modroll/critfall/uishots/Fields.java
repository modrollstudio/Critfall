package studio.modroll.critfall.uishots;

import java.lang.reflect.Field;

/**
 * Private fields this dev-only tool reaches into where vanilla and Critfall offer no accessor; dev runs
 * use Mojang names on both loaders, so the names hold.
 */
final class Fields {

    private Fields() {}

    static void set(Class<?> owner, Object target, String name, Object value) {
        try {
            field(owner, name).set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("UI shots cannot set " + owner.getSimpleName() + "." + name, e);
        }
    }

    static Object get(Class<?> owner, Object target, String name) {
        try {
            return field(owner, name).get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("UI shots cannot read " + owner.getSimpleName() + "." + name, e);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
