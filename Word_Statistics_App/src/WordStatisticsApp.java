import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordStatisticsApp extends JFrame {
    private final JTextField directoryField;
    private final JCheckBox includeSubdirectoriesCheckbox;
    private final DefaultTableModel tableModel;
    private final JTable statisticsTable;
    private final JButton start_Button;
    private final DefaultTableModel table;
    private final JTable statisticsTable1;
    private final List<File> filesToProcess = new ArrayList<>();
    private final Object lock = new Object();
    private int currentFileIndex = 0;
    public WordStatisticsApp() {
        setTitle("Word Statistics Calculator");
        setSize(800, 400);
        
        setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel directoryLabel = new JLabel("Directory:");
        directoryField = new JTextField(30);
        JButton browseButton = new JButton("Browse");
        start_Button = new JButton("Start Processing");
        browseButton.addActionListener(e -> browseDirectory());
        start_Button.addActionListener(e -> startprocess());
        includeSubdirectoriesCheckbox = new JCheckBox("Include Subdirectories");

        inputPanel.add(directoryLabel);
        inputPanel.add(directoryField);
        inputPanel.add(browseButton);
        inputPanel.add(includeSubdirectoriesCheckbox);
        inputPanel.add(start_Button);
        
          
          

        tableModel = new DefaultTableModel(new String[]{"File Name", "# Words", "Longest Word", "Shortest Word", "# is", "# are", "# you"}, 0);
        statisticsTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(statisticsTable);
        
        table = new DefaultTableModel(new String[]{"File", "Longest Word", "Shortest Word"}, 0);
        statisticsTable1 = new JTable(table);
        JScrollPane scrollPane1 = new JScrollPane(statisticsTable1);
        panel.add(inputPanel);
        panel.add(scrollPane);
        panel.add(scrollPane1);

        panel.setBorder(new EmptyBorder(10, 10, 30, 10));
        add(panel);
         
        

    }
    

    File selectedDirectory;
    boolean includeSubdirectories = false;

    public void browseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int choosen = chooser.showOpenDialog(this);

        if (choosen == JFileChooser.APPROVE_OPTION) {
            selectedDirectory = chooser.getSelectedFile();
            directoryField.setText(selectedDirectory.getAbsolutePath());
            includeSubdirectories = includeSubdirectoriesCheckbox.isSelected();
        }
    }
    private String globalLongestWord = "";
    private String globalShortestWord = ""; 
    
    public void startprocess() {
    tableModel.setRowCount(0);
    currentFileIndex = 0;
    filesToProcess.clear();
    
    // Reset global statistics variables
    globalLongestWord = "";
    globalShortestWord = "";
    
    processDirectory(selectedDirectory, includeSubdirectories);
    startProcessingThreads();
}
    
    private void processDirectory(File directory, boolean includeSubdirectories) {
        tableModel.setRowCount(0); 
        table.setRowCount(0); // Clear previous directory statistics

        if (directory.isDirectory()) {
            filesToProcess.clear(); // Clear previous files
            processFilesInDirectory(directory, includeSubdirectories);
            startProcessingThreads();
        } else {
            JOptionPane.showMessageDialog(this, "Invalid directory path.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void processFilesInDirectory(File directory, boolean includeSubdirectories) {
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".txt")) {
                    synchronized (lock) {
                        filesToProcess.add(file);
                    }
                } else if (includeSubdirectories && file.isDirectory()) {
                    // Recursive call for subdirectories
                    processFilesInDirectory(file, true);
                }
            }
        }
    }
    private void startProcessingThreads() {
        int numThreads = 5; 

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(new FileProcessor());
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
        }
        
        updateDirectoryStatistics(selectedDirectory);

        updateGlobalStatistics();
    }

     
    
    private class FileProcessor implements Runnable {
        @Override
        public void run() {
            while (currentFileIndex < filesToProcess.size()) {
                File file;
                synchronized (lock) {
                    if (currentFileIndex < filesToProcess.size()) {
                        file = filesToProcess.get(currentFileIndex);
                        currentFileIndex++;
                    } else {
                        break; 
                    }
                }

                WordStats wordStats = processFile(file);

                synchronized (lock) {
                    if (wordStats.getLongestWord().length() > globalLongestWord.length()) {
                        globalLongestWord = wordStats.getLongestWord();
                    }

                    if (wordStats.getShortestWord().length() < globalShortestWord.length() || globalShortestWord.isEmpty()) {
                        globalShortestWord = wordStats.getShortestWord();
                    }

                    // Add the row to the table
                    tableModel.addRow(new Object[]{file.getName(), wordStats.getNumWords(), wordStats.getLongestWord(),
                            wordStats.getShortestWord(), wordStats.getIsCount(), wordStats.getAreCount(), wordStats.getYouCount()});
                }
            }
        }
    }
    private void updateDirectoryStatistics(File directory) {
        WordStats directoryStats = new WordStats();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String fileName = (String) tableModel.getValueAt(i, 0);
            if (fileName.equals("All Directories") || fileName.equals("Total")) {
                continue;
            }

            // Check if the file belongs to the current directory
            if (new File(selectedDirectory, fileName).equals(directory)) {
                String longestWord = (String) tableModel.getValueAt(i, 2);
                String shortestWord = (String) tableModel.getValueAt(i, 3);

                directoryStats.updateFromRow(longestWord, shortestWord);
            }
        }

        // Add or update the row for the current directory in the directory statistics table
        boolean directoryRowExists = false;
        for (int i = 0; i < table.getRowCount(); i++) {
            if (table.getValueAt(i, 0).equals(directory.getAbsolutePath())) {
                directoryRowExists = true;
                table.setValueAt(directoryStats.getLongestWord(), i, 1);
                table.setValueAt(directoryStats.getShortestWord(), i, 2);
                break;
            }
        }

        if (!directoryRowExists) {
            table.addRow(new Object[]{directory.getAbsolutePath(), directoryStats.getLongestWord(), directoryStats.getShortestWord()});
        }
    }
  private void updateGlobalStatistics() {
    // Check if the row already exists in the table for global statistics
    int globalRowIndex = -1;
    int directoryRowIndex = -1;

    for (int i = 0; i < table.getRowCount(); i++) {
        if (table.getValueAt(i, 0).equals("All")) {
            globalRowIndex = i;
        } else if (table.getValueAt(i, 0).equals(selectedDirectory.getAbsolutePath())) {
            directoryRowIndex = i;
        }
    }

    // If the directory row exists, remove it
    if (directoryRowIndex != -1) {
        table.removeRow(directoryRowIndex);
    }

    // If the global row exists, update it; otherwise, add a new row
    if (globalRowIndex != -1) {
        table.setValueAt(globalLongestWord, globalRowIndex, 1);
        table.setValueAt(globalShortestWord, globalRowIndex, 2);
    } else {
        // Add the new row to the table
        table.addRow(new Object[]{"All", globalLongestWord, globalShortestWord});
    }
}

private WordStats processFile(File file) {
        WordStats wordStats = new WordStats();
        try (Scanner scanner = new Scanner(new FileInputStream(file))) {
            StringBuilder content = new StringBuilder();
            while (scanner.hasNext()) {
                content.append(scanner.next()).append(" ");
            }
            String fileContent = content.toString();
            wordStats.setNumWords(countWords(fileContent));
            wordStats.setLongestWord(findLongestWord(fileContent));
            wordStats.setShortestWord(findShortestWord(fileContent));
            wordStats.setIsCount(countOccurrences(fileContent, "is"));
            wordStats.setAreCount(countOccurrences(fileContent, "are"));
            wordStats.setYouCount(countOccurrences(fileContent, "you"));
        } catch (FileNotFoundException ex) {
           
        }
        return wordStats;
    }

    private int countWords(String content) {
        String[] words = content.split("\\s+");
        return words.length;
    }

    private String findLongestWord(String content) {
        String[] words = content.split("\\s+");
        String longest = "";
        for (String word : words) {
            if (word.length() > longest.length()) {
                longest = word;
            }
        }
        return longest;
    }

    private String findShortestWord(String content) {
        String[] words = content.split("\\s+");
        String shortest = words[0];
        for (String word : words) {
            if (word.length() < shortest.length()) {
                shortest = word;
            }
        }
        return shortest;
    }

    private int countOccurrences(String content, String word) {
        int count = 0;

        Pattern pattern = Pattern.compile("\\b" + word + "\\b");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WordStatisticsApp app = new WordStatisticsApp();
            app.setVisible(true);
        });
    }

    private class WordStats {
        private int numWords;
        private String longestWord;
        private String shortestWord;
        private int isCount;
        private int areCount;
        private int youCount;

        public int getNumWords() {
            return numWords;
        }

        public void setNumWords(int numWords) {
            this.numWords = numWords;
        }

        public String getLongestWord() {
            return longestWord;
        }

        public void setLongestWord(String longestWord) {
            this.longestWord = longestWord;
        }

        public String getShortestWord() {
            return shortestWord;
        }

        public void setShortestWord(String shortestWord) {
            this.shortestWord = shortestWord;
        }

        public int getIsCount() {
            return isCount;
        }

        public void setIsCount(int isCount) {
            this.isCount = isCount;
        }

        public int getAreCount() {
            return areCount;
        }

        public void setAreCount(int areCount) {
            this.areCount = areCount;
        }

        public int getYouCount() {
            return youCount;
        }

        public void setYouCount(int youCount) {
            this.youCount = youCount;
        }

        // Update statistics based on the provided values
        public void updateFromRow(String longestWord, String shortestWord) {
            if (longestWord.length() > this.longestWord.length()) {
                this.longestWord = longestWord;
            }

            if (shortestWord.length() < this.shortestWord.length()) {
                this.shortestWord = shortestWord;
            }
        }
    }

}
