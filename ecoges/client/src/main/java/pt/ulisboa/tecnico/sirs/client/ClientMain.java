package pt.ulisboa.tecnico.sirs.client;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {
	private static String serverHost = "localhost";
	private static int serverPort = 8000;

	private static String hashedToken = null;
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

		System.out.println(">>> " + ClientMain.class.getSimpleName() + " <<<");
		System.out.println("Setting up server connection on " + serverHost + ":" + serverPort);

		try {
			Client.init(serverHost, serverPort);
			showInterface();
		} catch (IOException e) {
			System.out.println("Could not start employee. Invalid backoffice certificate.");
		}
	}

	public static int getPlan() {
		String option;
		int plan = -1;
		Scanner scanner = new Scanner(System.in);

		while (plan != 1 && plan != 2) {
			System.out.println("Select one energy plan:\n" +
					"1. Flat Rate\n" +
					"2. Bi-Hourly Rate\n" +
					"0. Cancel");
			System.out.print("> ");
			option = scanner.nextLine();
			switch(option) {
				case "1":
				case "2":
					try {
						plan = Integer.parseInt(option);
					} catch(NumberFormatException e){
						System.out.println("Could not get plan. Please try again.");
					}
					break;
				case "0":
					return -1;
				default:
					System.out.println("Invalid plan selected. Please try again.");
			}
		}

		return plan;
	}

	public static void register(){
		String password, address;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter a email: ");
		email = scanner.nextLine();
		System.out.print("Enter a password: ");
		password = scanner.nextLine();
		System.out.print("Enter an address: ");
		address = scanner.nextLine();

		int plan = getPlan();

		if (Client.register(email, password, address, plan)) {
			System.out.println("Successfully registered with email '" + email + "'. Please login.");
		}
		else{
			System.out.println("Could not register in the system. Please try again.");
		}
	}

	public static void login(){
		String password;
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter your email: ");
		email = scanner.nextLine();
		System.out.print("Enter your password: ");
		password = scanner.nextLine();

		hashedToken = Client.login(email, password);
		if (hashedToken != null) {
			System.out.println("Login successful.");
			showMenu();
		}
	}

	public static void showInterface() {
		String input, result;
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
					Client.close();
					System.out.println("Exiting...");
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
		System.out.println("Welcome, " + email + "!");
		while(true) {
			try {
				System.out.println("\nPlease select one option:\n" +
						"1. Check personal info\n" +
						"2. Check energy consumption\n" +
						"3. Update address\n" +
						"4. Update plan\n" +
						"0. Logout");
				System.out.print("> ");

				input = scanner.nextLine();
				switch(input) {
					case "1":
						result = Client.checkPersonalInfo(email, hashedToken);
						System.out.println(result);
						continue;
					case "2":
						result = Client.checkEnergyConsumption(email, hashedToken);
						System.out.println(result);
						continue;
					case "3":
						System.out.print("Insert a new address: ");
						input = scanner.nextLine();
						success = Client.updateAddress(email, input, hashedToken);
						if (success) {
							System.out.println("Successfully updated address.");
						}
						continue;
					case "4":
						int plan = getPlan();
						if (plan == -1) return;

						success = Client.updatePlan(email, plan, hashedToken);
						if (success) System.out.println("Successfully updated plan.");
						continue;
					case "0":
						success = Client.logout(email, hashedToken);
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