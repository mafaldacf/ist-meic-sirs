package pt.ulisboa.tecnico.sirs.admin;

import java.io.IOException;
import java.util.Scanner;

public class AdminMain {
	private static String serverHost = "localhost";
	private static int serverPort = 8000;

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
		String username, password;
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
		String username, password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter your username: ");
		username = scanner.nextLine();
		System.out.print("Enter your password: ");
		password = scanner.nextLine();
		if (Admin.login(username, password)) {
			System.out.println("Login successful.");
			showMenu(username);
		}
		else {
			System.out.println("Could not login into the system. Please try again.");
		}
	}

	public static void showInterface() {
		String input, result;
		Scanner scanner = new Scanner(System.in);
		while(true) {
			System.out.println("\nPlease select one option:\n" +
					"1. Register\n" +
					"2. Login\n" +
					"3. Test connection with Hello\n" +
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
				case "3":
					System.out.print("Enter your name: ");
					input = scanner.nextLine();
					result = Admin.hello(input);
					System.out.println(result);
					continue;
				case "0":
					scanner.close();
					Admin.close();
					System.out.println("Exiting employee...");
					return;
				default:
					System.out.println("Invalid command.");

			}
		}
	}

	public static void showMenu(String username) {
		String input, result;
		Scanner scanner = new Scanner(System.in);
		System.out.println("Welcome " + username);
		while(true) {
			try {
				System.out.println("\nPlease select one option:\n" +
						"1. List Users\n" +
						"2. Check User\n" +
						"3. Test connection with Hello\n" +
						"0. Logout");
				System.out.print("> ");

				input = scanner.nextLine();
				switch(input) {
					case "1":
						result = Admin.listUsers();
						System.out.println(result);
						continue;
					case "2":
						System.out.print("Please enter a valid user ID: ");
						input = scanner.nextLine();
						result = Admin.checkUser(Integer.parseInt(input));
						System.out.println(result);
						continue;
					case "3":
						System.out.print("Enter your name: ");
						input = scanner.nextLine();
						result = Admin.hello(input);
						System.out.println(result);
						continue;
					case "0":
						scanner.close();
						Admin.close();
						System.out.println("Exiting employee...");
						return;
					default:
						System.out.println("Invalid command.");

				}
			} catch (NumberFormatException e) {
				System.out.println("Invalid user ID.");
			}
		}
	}
}