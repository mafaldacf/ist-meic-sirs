package pt.ulisboa.tecnico.sirs.mobile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class MobileMain {
	private static String serverHost = "localhost";
	private static int serverPort = 8000;

	private static String hashedToken = null;
	private static String name = null;
	private static String email = null;

	// Usage: [<serverHost>] [<serverPort>]
	public static void main(String[] args) {
		try {
			if (args.length == 2) {
				serverHost = args[0];
				serverPort = Integer.parseInt(args[1]);
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverHost>] [<serverPort>]");
			return;
		}

		System.out.println(">>> " + MobileMain.class.getSimpleName() + " <<<");
		System.out.println("Setting up server connection on " + serverHost + ":" + serverPort);

		try {
			Mobile.init(serverHost, serverPort);
			showInterface();
		} catch (IOException e) {
			System.out.println("Could not start client. Invalid webserver certificate.");
		}
	}

	

	public static void login(){
		String password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter your email: ");
		email = scanner.nextLine();
		System.out.print("Enter your password: ");
		password = scanner.nextLine();

		ArrayList<String> response = Mobile.login(email, password);
		if (response != null) {
			name = response.get(0);
			hashedToken = response.get(1);
			System.out.println("Login successful.");
			showMenu();
		}
	}

	public static void showInterface() {
		String input;
		Scanner scanner = new Scanner(System.in);
		while(true) {
			System.out.println("\nPlease select one option:\n" +
					"1. Login\n" +
					"0. Exit");
			System.out.print("> ");

			input = scanner.nextLine();
			switch(input) {
				case "1":
					login();
					continue;
				case "0":
					scanner.close();
					Mobile.close();
					System.out.println("Exiting...");
					return;
				default:
					System.out.println("Invalid command.");

			}
		}
	}

	public static void authorize_2FA(){

	}

	public static void showMenu() {
		String input;
		Scanner scanner = new Scanner(System.in);
		while(true) {
			System.out.println("\nPlease select one option:\n" +
					"1. Authorize Action\n" +
					"2. Ping\n" +
					"0. Exit");
			System.out.print("> ");

			input = scanner.nextLine();
			switch(input) {
				case "1":
					authorize_2FA();
					continue;
				case "2":
					System.out.println("Pong!");
					continue;
				case "0":
					scanner.close();
					Mobile.close();
					System.out.println("Exiting...");
					return;
				default:
					System.out.println("Invalid command.");

			}
		}
	}

	
}