module com.udacity.catpoint.securityservice {
    requires java.desktop;
    requires java.prefs;
    requires com.udacity.catpoint.imageservice;
    requires com.google.common;
    requires com.google.gson;
    requires miglayout.swing;

    opens com.udacity.catpoint.data to com.google.gson;
}
