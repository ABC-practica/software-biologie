package org.abc;

import javafx.application.Application;

public class Main {

    public static void main(String[] args) {
        System.out.println("[INFO] Starting application Main.main()...");
        try {
            Application.launch(App.class, args);
        } catch (Throwable t) {
            System.err.println("[FATAL] Application launching failed in Main.main(): " + t.getMessage());
            t.printStackTrace();
        }
    }
}