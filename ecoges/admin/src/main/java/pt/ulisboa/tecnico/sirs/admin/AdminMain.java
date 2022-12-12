package pt.ulisboa.tecnico.sirs.admin;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class AdminMain {
	private static String serverHost = "localhost";
	private static int serverPort = 8001;

	private static String username = null;
	private static String hashedToken = null;

	private static String role = null;

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
			System.out.println("Could not start admin. Invalid backoffice certificate.");
		}
	}

	public static void register(){
		String password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter your username: ");
		username = scanner.nextLine();
		System.out.print("Enter your password: ");
		password = scanner.nextLine();
		int role = getRole();
		if (Admin.register(username, password, role)) {
			System.out.println("Successfully registered with username '" + username + "'. Please login.");
		}
		else{
			System.out.println("Could not register in the system. Please try again.");
		}
	}

	public static int getRole() {
		String option;
		int role = -1;
		Scanner scanner = new Scanner(System.in);

		while (role != 1 && role != 2 && role != 3) {
			System.out.println("Select one energy plan:\n" +
					"1. Account Manager\n" +
					"2. Energy System Manager\n" +
					"0. Cancel");
			System.out.print("> ");
			option = scanner.nextLine();
			switch(option) {
				case "1":
				case "2":
					try {
						role = Integer.parseInt(option);
					} catch(NumberFormatException e){
						System.out.println("Could not get role. Please try again.");
					}
					break;
				case "0":
					return -1;
				default:
					System.out.println("Invalid role selected. Please try again.");
			}
		}

		return role;
	}

	public static void login(){
		String password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter your username: ");
		username = scanner.nextLine();
		System.out.print("Enter your password: ");
		password = scanner.nextLine();

		List<String> cred = Admin.login(username, password);
		if (cred != null) {
			role = cred.get(0);
			hashedToken = cred.get(1);
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
				System.out.println("\nYou are signed in as " + role + "\n" +
						"Please select one option:\n" +
						"1. List Clients\n" +
						"2. Check Client Personal Info\n" +
						"3. Check Client Energy Panel\n" +
						"4. Delete Client\n" +
						"0. Logout");
				System.out.print("> ");

				input = scanner.nextLine();
				switch(input) {
					case "1":
						result = Admin.listClients(username, hashedToken);
						System.out.println(result);
						continue;
					case "2":
						System.out.print("Enter the client email: ");
						input = scanner.nextLine();
						result = Admin.checkClientPersonalInfo(username, input, hashedToken);
						System.out.println(result);
						continue;
					case "3":
						System.out.print("Enter the client email: ");
						input = scanner.nextLine();
						result = Admin.checkClientEnergyPanel(username, input, hashedToken);
						System.out.println(result);
						continue;
					case "4":
						System.out.print("Enter the client email: ");
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
				System.out.println("Invalid command.");
			}
		}
	}
}