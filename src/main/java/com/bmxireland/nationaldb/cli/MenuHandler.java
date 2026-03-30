package com.bmxireland.nationaldb.cli;

import java.util.Scanner;

/**
 * Implemented by each interactive menu action.
 */
@FunctionalInterface
public interface MenuHandler {
    void handle(Scanner scanner);
}
