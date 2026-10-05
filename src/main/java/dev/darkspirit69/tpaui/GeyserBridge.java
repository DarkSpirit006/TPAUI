package dev.darkspirit69.tpaui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional, reflection-only bridge to Geyser/Floodgate so the core plugin has
 * no hard dependency on their APIs or their Java baseline.
 */
final class GeyserBridge {
    interface FormAction {
        void run();
    }

    interface CustomFormAction {
        void run(boolean toggleValue);
    }

    static final class FormButton {
        private final String text;
        private final FormAction action;

        FormButton(String text, FormAction action) {
            this.text = text;
            this.action = action;
        }
    }

    private final TPAUIPlugin plugin;
    private ApiHandle handle;
    private boolean warnedAboutApi;

    GeyserBridge(TPAUIPlugin plugin) {
        this.plugin = plugin;
    }

    void detect() {
        handle = findGeyserHandle();
        if (handle != null) {
            plugin.getLogger().info("Geyser forms integration detected via " + handle.description + ".");
        } else {
            plugin.getLogger().info("Geyser API not detected; Bedrock players will use "
                    + "the inventory fallback where possible.");
        }
    }

    boolean isBedrockPlayer(Player player) {
        ApiHandle api = handle;
        if (api == null || player == null) {
            return false;
        }
        try {
            Object value = api.isBedrock.invoke(api.instance, player.getUniqueId());
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warnOnce("Could not check whether a player is connected through Geyser: " + usefulMessage(ex));
            return false;
        }
    }

    boolean showSimpleForm(final Player player, String title, String content, List<FormButton> buttons) {
        ApiHandle api = handle;
        if (api == null || player == null || buttons == null || buttons.isEmpty()) {
            return false;
        }

        try {
            Object builder = buildSimpleFormBuilder(api.classLoader, title, content, buttons, player);
            return builder != null && sendForm(api, player, builder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warnOnce("Could not open a Geyser form: " + usefulMessage(ex));
            return false;
        }
    }

    boolean showCustomForm(
            Player player,
            String title,
            String content,
            String toggleLabel,
            boolean toggleDefault,
            CustomFormAction action) {
        return showCustomForm(player, title, content, toggleLabel, toggleDefault, action, null);
    }

    boolean showCustomForm(
            final Player player,
            String title,
            String content,
            String toggleLabel,
            boolean toggleDefault,
            CustomFormAction action,
            FormAction closedAction) {
        ApiHandle api = handle;
        if (api == null || player == null || toggleLabel == null || action == null) {
            return false;
        }

        try {
            Object builder = buildCustomFormBuilder(
                    api.classLoader,
                    title,
                    content,
                    toggleLabel,
                    toggleDefault,
                    player,
                    action,
                    closedAction);
            return builder != null && sendForm(api, player, builder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warnOnce("Could not open a Geyser custom form: " + usefulMessage(ex));
            return false;
        }
    }

    private boolean sendForm(ApiHandle api, Player player, Object builder) throws ReflectiveOperationException {
        Object formOrBuilder = builder;
        Method sendMethod = findSendFormMethod(api.instance.getClass(), builder);
        if (sendMethod == null) {
            Object form = buildForm(builder, api.classLoader);
            if (form == null) {
                return false;
            }
            formOrBuilder = form;
            sendMethod = findSendFormMethod(api.instance.getClass(), form);
        }
        if (sendMethod == null) {
            warnOnce("The installed Geyser API does not expose a compatible sendForm method.");
            return false;
        }

        Object result = sendMethod.invoke(api.instance, player.getUniqueId(), formOrBuilder);
        return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
    }

    private Object buildSimpleFormBuilder(
            ClassLoader loader,
            String title,
            String content,
            List<FormButton> buttons,
            final Player player) throws ReflectiveOperationException {
        Class<?> simpleFormClass = loadClass("org.geysermc.cumulus.form.SimpleForm", loader);
        if (simpleFormClass == null) {
            // Compatibility with older Cumulus packages.
            simpleFormClass = loadClass("org.geysermc.cumulus.SimpleForm", loader);
        }
        if (simpleFormClass == null) {
            warnOnce("Cumulus SimpleForm is unavailable in the installed Geyser build.");
            return null;
        }

        Method builderFactory = simpleFormClass.getMethod("builder");
        Object builder = builderFactory.invoke(null);
        if (builder == null) {
            return null;
        }

        Method titleMethod = findFluentMethod(builder.getClass(), "title", String.class);
        Method contentMethod = findFluentMethod(builder.getClass(), "content", String.class);
        if (titleMethod == null || contentMethod == null) {
            warnOnce("The installed Cumulus API is missing SimpleForm title/content methods.");
            return null;
        }
        titleMethod.invoke(builder, title);
        contentMethod.invoke(builder, content);

        Method buttonWithCallback = findButtonCallbackMethod(builder.getClass());
        if (buttonWithCallback != null) {
            for (final FormButton button : buttons) {
                Consumer<Object> callback = new Consumer<Object>() {
                    @Override
                    public void accept(Object ignored) {
                        runOnServerThread(player, button.action);
                    }
                };
                buttonWithCallback.invoke(builder, button.text, callback);
            }
            return builder;
        }

        Method buttonMethod = findFluentMethod(builder.getClass(), "button", String.class);
        if (buttonMethod == null) {
            warnOnce("The installed Cumulus API is missing SimpleForm button methods.");
            return null;
        }
        for (FormButton button : buttons) {
            buttonMethod.invoke(builder, button.text);
        }

        Method handlerMethod = findConsumerHandlerMethod(builder.getClass());
        if (handlerMethod == null) {
            warnOnce("The installed Cumulus API cannot register a SimpleForm response handler.");
            return null;
        }
        final List<FormButton> capturedButtons = new ArrayList<FormButton>(buttons);
        Consumer<Object> responseHandler = new Consumer<Object>() {
            @Override
            public void accept(Object response) {
                int buttonIndex = readButtonIndex(response, 0);
                if (buttonIndex >= 0 && buttonIndex < capturedButtons.size()) {
                    runOnServerThread(player, capturedButtons.get(buttonIndex).action);
                }
            }
        };
        handlerMethod.invoke(builder, responseHandler);
        return builder;
    }

    private Object buildCustomFormBuilder(
            ClassLoader loader,
            String title,
            String content,
            String toggleLabel,
            boolean toggleDefault,
            final Player player,
            final CustomFormAction action,
            final FormAction closedAction) throws ReflectiveOperationException {
        Class<?> customFormClass = loadClass("org.geysermc.cumulus.form.CustomForm", loader);
        if (customFormClass == null) {
            customFormClass = loadClass("org.geysermc.cumulus.CustomForm", loader);
        }
        if (customFormClass == null) {
            warnOnce("Cumulus CustomForm is unavailable in the installed Geyser build.");
            return null;
        }

        Object builder = customFormClass.getMethod("builder").invoke(null);
        if (builder == null) {
            return null;
        }

        Method titleMethod = findFluentMethod(builder.getClass(), "title", String.class);
        Method labelMethod = findFluentMethod(builder.getClass(), "label", String.class);
        if (titleMethod == null || labelMethod == null) {
            warnOnce("The installed Cumulus API is missing CustomForm title/label methods.");
            return null;
        }
        titleMethod.invoke(builder, title);
        labelMethod.invoke(builder, content);

        Method toggleMethod = findFluentMethod(builder.getClass(), "toggle", String.class, boolean.class);
        if (toggleMethod == null) {
            warnOnce("The installed Cumulus API is missing CustomForm toggle defaults.");
            return null;
        }
        toggleMethod.invoke(builder, toggleLabel, Boolean.valueOf(toggleDefault));

        Method handlerMethod = findConsumerHandlerMethod(builder.getClass());
        if (handlerMethod == null) {
            warnOnce("The installed Cumulus API cannot register a CustomForm response handler.");
            return null;
        }
        Consumer<Object> responseHandler = new Consumer<Object>() {
            @Override
            public void accept(final Object response) {
                final boolean toggleValue = readToggleValue(response);
                runOnServerThread(player, new FormAction() {
                    @Override
                    public void run() {
                        action.run(toggleValue);
                    }
                });
            }
        };
        handlerMethod.invoke(builder, responseHandler);
        if (closedAction != null) {
            Method closedHandler = findClosedResultHandlerMethod(builder.getClass());
            if (closedHandler != null) {
                Runnable callback = new Runnable() {
                    @Override
                    public void run() {
                        runOnServerThread(player, closedAction);
                    }
                };
                closedHandler.invoke(builder, callback);
            }
        }
        return builder;
    }

    private Method findClosedResultHandlerMethod(Class<?> builderClass) {
        for (Method method : allMethods(builderClass)) {
            Class<?>[] parameters = method.getParameterTypes();
            if ("closedResultHandler".equals(method.getName())
                    && parameters.length == 1 && parameters[0] == Runnable.class) {
                return method;
            }
        }
        return null;
    }

    private Object buildForm(Object builder, ClassLoader loader) {
        try {
            Class<?> formBuilder = loadClass("org.geysermc.cumulus.form.util.FormBuilder", loader);
            if (formBuilder == null) {
                formBuilder = loadClass("org.geysermc.cumulus.form.FormBuilder", loader);
            }
            if (formBuilder != null) {
                return formBuilder.getMethod("build").invoke(builder);
            }
            return findNoArgMethod(builder.getClass(), "build").invoke(builder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null;
        }
    }

    private Method findSendFormMethod(Class<?> apiClass, Object formOrBuilder) {
        for (Method method : allMethods(apiClass)) {
            Class<?>[] params = method.getParameterTypes();
            if (!"sendForm".equals(method.getName()) || params.length != 2 || params[0] != UUID.class) {
                continue;
            }
            if (params[1].isInstance(formOrBuilder)) {
                return method;
            }
        }
        return null;
    }

    private Method findButtonCallbackMethod(Class<?> builderClass) {
        for (Method method : allMethods(builderClass)) {
            Class<?>[] params = method.getParameterTypes();
            if ("button".equals(method.getName()) && params.length == 2
                    && params[0] == String.class && params[1] == Consumer.class) {
                return method;
            }
        }
        return null;
    }

    private Method findConsumerHandlerMethod(Class<?> builderClass) {
        for (Method method : allMethods(builderClass)) {
            Class<?>[] params = method.getParameterTypes();
            if (("validResultHandler".equals(method.getName()) || "responseHandler".equals(method.getName()))
                    && params.length == 1 && params[0] == Consumer.class) {
                return method;
            }
        }
        return null;
    }

    private Method findFluentMethod(Class<?> type, String name, Class<?>... params) {
        for (Method method : allMethods(type)) {
            if (name.equals(method.getName()) && matches(method.getParameterTypes(), params)) {
                return method;
            }
        }
        return null;
    }

    private Method findNoArgMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : allMethods(type)) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private boolean matches(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private List<Method> allMethods(Class<?> type) {
        List<Method> methods = new ArrayList<Method>();
        collectMethods(type, methods);
        return methods;
    }

    private void collectMethods(Class<?> type, List<Method> methods) {
        if (type == null) {
            return;
        }
        for (Method method : type.getMethods()) {
            if (!methods.contains(method)) {
                methods.add(method);
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectMethods(iface, methods);
        }
        collectMethods(type.getSuperclass(), methods);
    }

    private boolean readToggleValue(Object response) {
        Object value = invokeNoArg(response, "asToggle", "getToggle", "toggle");
        if (value == null) {
            value = invokeNoArg(response, "next");
        }
        return toToggleValue(value, 0);
    }

    private boolean toToggleValue(Object value, int depth) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        if (value == null || depth >= 3) {
            return false;
        }
        Object nested = invokeNoArg(value, "getValue", "value", "isToggled", "isSelected");
        return nested != value && toToggleValue(nested, depth + 1);
    }

    private int readButtonIndex(Object response, int depth) {
        if (response == null || depth > 3) {
            return -1;
        }
        Object value = invokeNoArg(response, "clickedButtonId", "getClickedButtonId", "buttonId", "getButtonId");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        Object nested = invokeNoArg(response, "response", "getResponse", "result", "getResult");
        return nested == response ? -1 : readButtonIndex(nested, depth + 1);
    }

    private void runOnServerThread(final Player player, final FormAction action) {
        if (player == null || action == null || !plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && plugin.isEnabled()) {
                        action.run();
                    }
                }
            });
        } catch (RuntimeException ex) {
            // The plugin was disabled between the response and task scheduling.
        }
    }

    private ApiHandle findGeyserHandle() {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        for (Plugin candidate : plugins) {
            if (!candidate.isEnabled()) {
                continue;
            }
            String name = candidate.getName();
            if (name.equalsIgnoreCase("Geyser-Spigot") || name.equalsIgnoreCase("Geyser-Bukkit")
                    || name.equalsIgnoreCase("Geyser")) {
                ApiHandle geyser = createHandle(
                        candidate,
                        "org.geysermc.geyser.api.GeyserApi",
                        "api",
                        "isBedrockPlayer");
                if (geyser != null) {
                    geyser.description = name;
                    return geyser;
                }
            }
        }
        for (Plugin candidate : plugins) {
            if (candidate.isEnabled() && candidate.getName().equalsIgnoreCase("Floodgate")) {
                ApiHandle floodgate = createHandle(
                        candidate,
                        "org.geysermc.floodgate.api.FloodgateApi",
                        "getInstance",
                        "isFloodgatePlayer");
                if (floodgate != null) {
                    floodgate.description = "Floodgate";
                    return floodgate;
                }
            }
        }
        return null;
    }

    private ApiHandle createHandle(Plugin plugin, String className, String factoryName, String bedrockMethodName) {
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(className, true, loader);
            Object instance = apiClass.getMethod(factoryName).invoke(null);
            if (instance == null) {
                return null;
            }
            Method bedrock = apiClass.getMethod(bedrockMethodName, UUID.class);
            return new ApiHandle(loader, instance, bedrock, plugin.getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private Class<?> loadClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, true, loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            try {
                return Class.forName(name);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError alsoIgnored) {
                return null;
            }
        }
    }

    private Object invokeNoArg(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next compatibility name.
            }
        }
        return null;
    }

    private void warnOnce(String message) {
        if (!warnedAboutApi) {
            warnedAboutApi = true;
            plugin.getLogger().warning(message);
        }
    }

    private String usefulMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) current).getCause();
            if (cause == null) {
                break;
            }
            current = cause;
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    private static final class ApiHandle {
        private final ClassLoader classLoader;
        private final Object instance;
        private final Method isBedrock;
        private String description;

        private ApiHandle(ClassLoader classLoader, Object instance, Method isBedrock, String description) {
            this.classLoader = classLoader;
            this.instance = instance;
            this.isBedrock = isBedrock;
            this.description = description;
        }
    }

}
