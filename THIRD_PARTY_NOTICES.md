# Third-party notices

TPAUI includes the following runtime dependencies in its distributable JAR:

- [Gson 2.13.1](https://github.com/google/gson) — licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). A copy is bundled as `META-INF/LICENSE-GSON.txt` in the plugin JAR.
- [bStats Metrics 3.2.1](https://github.com/Bastian/bstats-metrics) — licensed under the [MIT License](https://opensource.org/licenses/mit-license.php). A copy is bundled as `META-INF/LICENSE-BSTATS.txt` in the plugin JAR.

Gson and bStats packages are relocated inside the TPAUI JAR to avoid conflicts with server-provided libraries. The Spigot API is a compile-only dependency and is not bundled.

When `settings.bstats-enabled` is `true`, TPAUI reports bStats' standard anonymous plugin/server metrics using project ID `34518`. Set it to `false` to disable TPAUI's metrics; the server-wide bStats setting is also respected.
