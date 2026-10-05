package dev.darkspirit69.tpaui;

enum RequestMode {
    TPA("tpa"),
    TPAHERE("tpahere");

    final String command;

    RequestMode(String command) {
        this.command = command;
    }
}
