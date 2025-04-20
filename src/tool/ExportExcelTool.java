package tool;


import org.apache.poi.ss.usermodel.*;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog.ModalityType;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * @author K0dan
 */
public class ExportExcelTool {

    private final JTable resultTable;
    private final LoginAndFetchTool fetchTool;

    public ExportExcelTool(JTable table, LoginAndFetchTool fetchTool) {
        this.resultTable = table;
        this.fetchTool = fetchTool;
    }

    public void exportToExcel() {
        String defaultFileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")) + ".xlsx";

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("選擇存放 Excel 檔案的位置");
        fileChooser.setSelectedFile(new File(defaultFileName));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        int userSelection = fileChooser.showSaveDialog(null);

        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String path = fileChooser.getSelectedFile().getAbsolutePath();

        final JDialog progressDialog = new JDialog();
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        String dumpLabel = "Excel 倒出中，請稍候...";
        JLabel label = new JLabel(dumpLabel);
        progressDialog.setTitle(dumpLabel);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.add(label, BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        progressDialog.setSize(300, 80);
        progressDialog.setLocationRelativeTo(null);
        progressDialog.setModalityType(ModalityType.APPLICATION_MODAL);

        SwingWorker<Void, Integer> task = new SwingWorker<Void, Integer>() {

            @Override
            protected Void doInBackground() throws InterruptedException {
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("Simple");

                String[] headers = {
                    "維修案號", "派修類別", "派修項目", "叫修院區", "叫修單位",
                    "報修人", "分機號碼", "提出時間", "到場時間", "完成時間",
                    "問題描述", "維修內容", "維護廠商", "工程師", "維修狀態",
                    "對工程師的整體滿意度", "對工程師的服務態度", "對此次維修時效滿意度"
                };

                CellStyle borderedStyle = createBorderedCellStyle(workbook);

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(borderedStyle);
                }

                DefaultTableModel model = (DefaultTableModel) resultTable.getModel();
                List<Object[]> filteredData = filterAndSortData(model);

                int totalRows = filteredData.size();
                int rowNum = 1;

                for (Object[] rowData : filteredData) {
                    Row row = sheet.createRow(rowNum);
                    for (int colNum = 0; colNum < headers.length; colNum++) {
                        Cell cell = row.createCell(colNum);
                        cell.setCellStyle(borderedStyle);
                        if (rowData[colNum] != null) {
                            cell.setCellValue(rowData[colNum].toString());
                        } else {
                            cell.setCellValue("");
                        }
                    }

                    int progress = (int) ((rowNum * 100.0) / totalRows);
                    publish(progress);
                    rowNum++;
                    Thread.sleep(50);
                }

                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                try (FileOutputStream fileOut = new FileOutputStream(path)) {
                    workbook.write(fileOut);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "檔案寫入發生錯誤: " + e.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE));
                }

                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int mostRecentValue = chunks.get(chunks.size() - 1);
                progressBar.setValue(mostRecentValue);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                JOptionPane.showMessageDialog(null, "Excel 匯出成功！\n位置：" + path, "完成", JOptionPane.INFORMATION_MESSAGE);
                try {
                    Desktop.getDesktop().open(fileChooser.getCurrentDirectory());
                } catch (IOException ex) {
                    Logger.getLogger(ExportExcelTool.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };

        task.execute();
        progressDialog.setVisible(true);
    }

    private CellStyle createBorderedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.BOTTOM);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private List<Object[]> filterAndSortData(DefaultTableModel model) {
        List<Object[]> filteredList = new ArrayList<>();

        for (int i = 0; i < model.getRowCount(); i++) {

            String status = model.getValueAt(i, 3).toString();
            String date = model.getValueAt(i, 8).toString();
            String engineer = model.getValueAt(i, 7).toString();
            String[] engineerParts = engineer.split(" ", 2);
            if (!status.equals("完成") && !status.equals("結案") || !fetchTool.isDateBetween(date)) {
                continue;
            }

            String caseIdStr = model.getValueAt(i, 0).toString();
            String categoryId = model.getValueAt(i, 2).toString();
            String repairUnit = model.getValueAt(i, 5).toString();
            String extension = model.getValueAt(i, 6).toString();

            String uuid = model.getValueAt(i, 9).toString();

            String[] parts = categoryId.split(" ", 2);
            String category = parts[0];
            String categoryName = parts.length > 1 ? parts[1] : "";

            String[] details = fetchTool.getCaseDetails(uuid);
            String arrivalTime = details[0];
            String completionTime = details[1];
            String issueDescription = details[2];
            String maintenanceContent = details[3];

            String[] unitParts = repairUnit.split("/", 2);
            String callUnit = unitParts[0];

            String reportPerson;
            String extensionNum = "";
            if (extension.contains(" ")) {
                String[] extensionParts = extension.split(" ", 2);
                reportPerson = extensionParts[0];
                extensionNum = extensionParts.length > 1 ? extensionParts[1] : "";
            } else {
                reportPerson = extension;
            }

            Object[] rowData = new Object[18];
            rowData[0] = caseIdStr;
            rowData[1] = category;
            rowData[2] = categoryName;
            rowData[3] = "國防醫學院";
            rowData[4] = callUnit;
            rowData[5] = reportPerson;
            rowData[6] = extensionNum;
            rowData[7] = date;
            rowData[8] = arrivalTime;
            rowData[9] = completionTime;
            rowData[10] = issueDescription;
            rowData[11] = maintenanceContent;
            rowData[12] = "公司";
            rowData[13] = engineerParts.length > 1 ? engineerParts[1] : "";
            rowData[14] = "結案";

            rowData[15] = ""; // 工程師整體滿意度
            rowData[16] = ""; // 工程師的服務態度
            rowData[17] = ""; // 維修時效滿意度

            filteredList.add(rowData);
        }

        filteredList.sort(Comparator.comparing(o -> Long.valueOf(o[0].toString())));
        return filteredList;
    }
}
