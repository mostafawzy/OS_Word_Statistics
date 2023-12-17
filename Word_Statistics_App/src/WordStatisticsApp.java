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
    
    private int currentFileIndex ;
    
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
    private String globalLongestWord ;
    private String globalShortestWord ; 
    
    public void startprocess() {
    currentFileIndex = 0;
    // Reset global statistics variables
    globalLongestWord = "";
    globalShortestWord = "";
    
    processDirectory(selectedDirectory, includeSubdirectories);
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
        updateGlobalStatistics();
    }
 private void updateGlobalStatistics() {
    int globalRowIndex = findRowIndex("All");
    int directoryRowIndex = findRowIndex(selectedDirectory.getAbsolutePath());

    if (directoryRowIndex != -1) {
        table.removeRow(directoryRowIndex);
    }

    if (globalRowIndex != -1) {
        updateRow(globalRowIndex, globalLongestWord, globalShortestWord);
    } else {
        addNewRow("All", globalLongestWord, globalShortestWord);
    }
}
private int findRowIndex(String label) {
    for (int i = 0; i < table.getRowCount(); i++) {
        if (table.getValueAt(i, 0).equals(label)) {
            return i;
        }
    }
    return -1;
}
private void updateRow(int rowIndex, Object longestWord, Object shortestWord) {
    table.setValueAt(longestWord, rowIndex, 1);
    table.setValueAt(shortestWord, rowIndex, 2);
}
private void addNewRow(String label, Object longestWord, Object shortestWord) {
    table.addRow(new Object[]{label, longestWord, shortestWord});
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

       
    }
}
