package com.br.remote_agent.control;

import java.awt.Robot;
import java.awt.Dimension;
import java.awt.Toolkit;


public class RemoteDesktopController {

    private final Robot robot;

    public RemoteDesktopController() throws Exception {
        this.robot = new Robot();
    }

    public void moveMouse(int x, int y) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        // Ignora coordenadas fora da tela
        if (x < 0 || y < 0 || x >= screenSize.width || y >= screenSize.height) {
            return;
        }

        robot.mouseMove(x, y);
    }
}
