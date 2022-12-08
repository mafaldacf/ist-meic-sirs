package pt.ulisboa.tecnico.sirs.admin;

import java.io.IOException;
import java.util.Scanner;

public class AdminMain {
	private static String serverHost = "localhost";
	private static int serverPort = 8001;

	private static String username = null;
	private static String hashedToken = null;

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

		System.out.println(">>> " + AdminMain.class.getSimpleName() + " <<<");
		System.out.println("Setting up server connection on " + serverHost + ":" + serverPort);

		try {
			Admin.init(serverHost, serverPort);
			showInterface();
		} catch (IOException e) {
			System.out.println("Could not start employee. Invalid backoffice certificate.");
		}
	}

	public static void register(){
		String password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter a username: ");
		username = scanner.nextLine();
		System.out.print("Enter a password: ");
		password = scanner.nextLine();
		if (Admin.register(username, password)) {
			System.out.println("Successfully registered with username '" + username + "'. Please login.");
		}
		else{
			System.out.println("Could not register in the system. Please try again.");
		}
	}

	public static void login(){
		String password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter your username: ");
		username = scanner.nextLine();
		System.out.print("Enter your password: ");
		password = scanner.nextLine();

		hashedToken = Admin.login(username, password);
		if (hashedToken != null) {
			System.out.println("Login successful.");
			showMenu();
		}
		else {
			System.out.println("Could not login into the system. Please try again.");
		}
	}

	public static void showInterface() {
		String input;
		Scanner scanner = new Scanner(System.in);
		while(true) {
			System.out.println("\nPlease select one option:\n" +
					"1. Register\n" +
					"2. Login\n" +
					"0. Exit");
			System.out.print("> ");

			input = scanner.nextLine();
			switch(input) {
				case "1":
					register();
					continue;
				case "2":
					login();
					continue;
				case "0":
					scanner.close();
					Admin.close();
					System.out.println("Exiting..");
					return;
				default:
					System.out.println("Invalid command.");

			}
		}
	}

	public static void showMenu() {
		String input, result;
		boolean success;
		Scanner scanner = new Scanner(System.in);
		System.out.println("Welcome, " + username + "!");
		while(true) {
			try {
				System.out.println("\nPlease select one option:\n" +
						"1. List Clients\n" +
						"2. Delete Client\n" +
						"0. Logout");
				System.out.print("> ");

				input = scanner.nextLine();
				switch(input) {
					case "1":
						result = Admin.listClients(username, hashedToken);
						System.out.println(result);
						continue;
					case "2":
						System.out.print("Enter a client email to delete: ");
						input = scanner.nextLine();
						success = Admin.deleteClient(username, input, hashedToken);
						if (success) {
							System.out.println("Successfully deleted client.");
						}
						continue;
					case "0":
						success = Admin.logout(username, hashedToken);
						if (success) {
							System.out.println("Logged out.");
							return;
						}
					default:
						System.out.println("Invalid command.");

				}
			} catch (NumberFormatException e) {
				System.out.println("Invalid user ID.");
			}
		}
	}
}