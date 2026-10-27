package com.nexuswms;

/** Plain main(): required so the JavaFX runtime is on the classpath when launching from a fat JAR or IDE. Run THIS class. */
public class NexusLauncher {
    public static void main(String[] args) {
        javafx.application.Application.launch(NexusWmsApplication.class, args);
    }
}
