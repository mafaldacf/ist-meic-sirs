package pt.ulisboa.tecnico.sirs.client;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {

	public static void main(String[] args) {
		String serverHost;
		int serverPort;

		if (args.length != 2) {
			System.out.println("Could not start client.");
			System.out.println("Usage: <serverHost> <serverPort>");
			return;
		}

		serverHost = args[0];
		serverPort = Integer.parseInt(args[1]);

		System.out.println(">>> " + ClientMain.class.getSimpleName() + " <<<");
		System.out.println("Setting up server connection on " + serverHost + ":" + serverPort);
		try {
			Client.init(serverHost, serverPort);
			showInterface();
		} catch (IOException e) {
			System.out.println("Could not start client. Invalid backoffice certificate.");
			return;
		}
	}

	public static void showInterface(){
		String input, result;
		Scanner scanner = new Scanner(System.in);
		while(true) {
			try {
				System.out.println("\nPlease select one option:\n" +
						"1. List Users\n" +
						"2. Check User\n" +
						"3. Test connection with Hello\n" +
						"0. Exit");
				System.out.print("> ");

				input = scanner.nextLine();
				switch(input) {
					case "1":
						result = Client.listUsers();
						System.out.println(result);
						continue;
					case "2":
						System.out.print("Please enter a valid user ID: ");
						input = scanner.nextLine();
						result = Client.checkUser(Integer.parseInt(input));
						System.out.println(result);
						continue;
					case "3":
						System.out.print("Enter your name: ");
						input = scanner.nextLine();
						result = Client.hello(input);
						System.out.println(result);
						continue;
					case "0":
						scanner.close();
						Client.close();
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