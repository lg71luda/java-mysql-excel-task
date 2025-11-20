import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class Main {
	// Настройки подключения к MYSQL
	private static final String DB_URL = "jdbc:mysql://localhost:3306/java_tasks?" +
			"useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";

	private static final String DB_USER = "root";
	private static final String DB_PASSWORD = "123456";

	private static Connection getConnection() throws SQLException {
		return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
	}

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		boolean continueProgram = true;

		// Загружаем драйвер
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			System.err.println("ОШИБКА: Не найден MySQL драйвер!");
			System.err.println("Убедитесь, что mysql-connector-j-*.jar добавлен в CLASSPATH.");
			return;
		}

		while (continueProgram) {
			System.out.println("\nМеню");
			System.out.println("1. Вывести все таблицы из MySQL");
			System.out.println("2. Создать таблицу в MySQL");
			System.out.println("3. Проверить числа на целостность и чётность");
			System.out.println("4. Сохранить данные из MySQL в Excel и вывести на экран");
			System.out.println("0. Выход");
			System.out.print("Выберите пункт: ");

			String choice = scanner.nextLine().trim();

			switch (choice) {
				case "1":
					listTables();
					break;
				case "2":
					createTable();
					break;
				case "3":
					analyzeNumbers();
					break;
				case "4":
					exportToExcel();
					break;
				case "0":
					continueProgram = false;
					System.out.println("Выход из программы.");
					break;
				default:
					System.out.println("Неверный выбор. Попробуйте снова.");
			}
		}
		scanner.close();
	}

	// 1. Вывести все таблицы
	private static void listTables() {
		try (Connection conn = getConnection()) {
			String catalog = conn.getCatalog(); // Получаем имя текущей базы
			ResultSet rs = conn.getMetaData().getTables(catalog, null, "%", new String[] { "TABLE" });

			System.out.println("\nТаблицы в базе данных '" + catalog + "':");
			boolean found = false;
			while (rs.next()) {
				System.out.println("- " + rs.getString("TABLE_NAME"));
				found = true;
			}
			if (!found)
				System.out.println("Нет таблиц.");
			rs.close();
		} catch (SQLException e) {
			System.err.println("Ошибка при получении таблиц: " + e.getMessage());
		}
	}

	// 2. Создать таблицу
	private static void createTable() {
		String sql = """
				CREATE TABLE IF NOT EXISTS number_analysis (
				    id INT AUTO_INCREMENT PRIMARY KEY,
				    input_value VARCHAR(50) NOT NULL,
				    is_integer BOOLEAN NOT NULL,
				    is_even BOOLEAN,
				    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
				""";

		try (Connection conn = getConnection();
				Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
			System.out.println("Таблица 'number_analysis' создана или уже существует.");
		} catch (SQLException e) {
			System.err.println("Ошибка при создании таблицы: " + e.getMessage());
		}
	}

	// 3. Анализ чисел
	private static void analyzeNumbers() {
		System.out.println("\nВведите числа через пробел (например: 12 3.5 abc -7):");
		Scanner inputScanner = new Scanner(System.in);
		String line = inputScanner.nextLine();
		String[] tokens = line.trim().split("\\s+");

		if (tokens.length == 0 || (tokens.length == 1 && tokens[0].isEmpty())) {
			System.out.println("Нет данных для анализа.");
			return;
		}

		List<NumberRecord> records = new ArrayList<>();
		for (String token : tokens) {
			NumberRecord rec = parseAndAnalyze(token);
			records.add(rec);
			String evenStr = rec.isInteger ? (rec.isEven ? "Да" : "Нет") : "—";
			System.out.printf("Ввод: %s → Целое: %s, Чётное: %s%n",
					token, rec.isInteger ? "Да" : "Нет", evenStr);
		}

		saveToDatabase(records);
	}

	private static NumberRecord parseAndAnalyze(String input) {
		try {
			double d = Double.parseDouble(input);
			if (d == Math.floor(d) && Double.isFinite(d)) {
				long value = (long) d;
				return new NumberRecord(input, true, (value % 2 == 0));
			} else {
				return new NumberRecord(input, false, false);
			}
		} catch (NumberFormatException e) {
			return new NumberRecord(input, false, false);
		}
	}

	private static void saveToDatabase(List<NumberRecord> records) {
		String sql = "INSERT INTO number_analysis (input_value, is_integer, is_even) VALUES (?, ?, ?)";
		try (Connection conn = getConnection();
				PreparedStatement stmt = conn.prepareStatement(sql)) {

			for (NumberRecord r : records) {
				stmt.setString(1, r.inputValue);
				stmt.setBoolean(2, r.isInteger);
				stmt.setObject(3, r.isInteger ? r.isEven : null, java.sql.Types.BOOLEAN);
				stmt.addBatch();
			}
			stmt.executeBatch();
			System.out.println("\nДанные сохранены в базу данных.");
		} catch (SQLException e) {
			System.err.println("Ошибка записи в БД: " + e.getMessage());
		}
	}

	// 4. Экспорт в Excel
	private static void exportToExcel() {
		List<NumberRecord> records = readFromDatabase();
		if (records.isEmpty()) {
			System.out.println("Нет данных для экспорта.");
			return;
		}

		// Создаём Excel-файл
		try (Workbook workbook = new XSSFWorkbook();
				java.io.FileOutputStream fileOut = new java.io.FileOutputStream("results.xlsx")) {

			Sheet sheet = workbook.createSheet("Результаты");
			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("Ввод");
			header.createCell(1).setCellValue("Целое?");
			header.createCell(2).setCellValue("Чётное?");

			for (int i = 0; i < records.size(); i++) {
				Row row = sheet.createRow(i + 1);
				NumberRecord r = records.get(i);
				row.createCell(0).setCellValue(r.inputValue);
				row.createCell(1).setCellValue(r.isInteger ? "Да" : "Нет");
				row.createCell(2).setCellValue(
						r.isInteger ? (r.isEven ? "Да" : "Нет") : "—");
			}

			workbook.write(fileOut);
			System.out.println("Файл 'results.xlsx' создан в текущей папке.");

			// Вывод в консоль
			System.out.println("\nЭкспортированные данные:");
			System.out.printf("%-15s %-10s %s%n", "Ввод", "Целое?", "Чётное?");
			System.out.println("----------------------------------------");
			for (NumberRecord r : records) {
				String evenStr = r.isInteger ? (r.isEven ? "Да" : "Нет") : "—";
				System.out.printf("%-15s %-10s %s%n", r.inputValue, r.isInteger ? "Да" : "Нет", evenStr);
			}

		} catch (Exception e) {
			System.err.println("Ошибка при создании Excel: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static List<NumberRecord> readFromDatabase() {
		List<NumberRecord> list = new ArrayList<>();
		String sql = "SELECT input_value, is_integer, is_even FROM number_analysis ORDER BY id";
		try (Connection conn = getConnection();
				PreparedStatement stmt = conn.prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				String input = rs.getString("input_value");
				boolean isInt = rs.getBoolean("is_integer");
				Boolean isEven = rs.getObject("is_even", Boolean.class);
				list.add(new NumberRecord(input, isInt, isEven == null ? false : isEven));
			}
		} catch (SQLException e) {
			System.err.println("Ошибка чтения из БД: " + e.getMessage());
		}
		return list;
	}

	// Вспомогательный класс (заменяет record для совместимости)
	private static class NumberRecord {
		final String inputValue;
		final boolean isInteger;
		final boolean isEven;

		NumberRecord(String inputValue, boolean isInteger, boolean isEven) {
			this.inputValue = inputValue;
			this.isInteger = isInteger;
			this.isEven = isEven;
		}
	}
}