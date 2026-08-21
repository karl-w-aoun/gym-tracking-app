package com.gymtrack.app;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(GymFilesPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
