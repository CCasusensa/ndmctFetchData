package tool;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog.ModalityType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
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
        
        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String path = fileChooser.getSelectedFile().getAbsolutePath();

        final JDialog progressDialog = new JDialog();
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        JLabel label = new JLabel("Excel 匯出中，正在向伺服器抓取詳細資料，請稍候...");
        
        progressDialog.setTitle("匯出進度");
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.add(label, BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        progressDialog.setSize(350, 80);
        progressDialog.setLocationRelativeTo(null);
        progressDialog.setModalityType(ModalityType.APPLICATION_MODAL);

        SwingWorker<Void, Integer> task = new SwingWorker<Void, Integer>() {

            @Override
            protected Void doInBackground() throws Exception {
                try (Workbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = workbook.createSheet("維修報表");

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
                    
                    LocalDate startDate = parseDateSafe(fetchTool.getStartDateText());
                    LocalDate endDate = parseDateSafe(fetchTool.getEndDateText());
                    
                    List<Object[]> filteredData = filterAndSortData(model, startDate, endDate);

                    int totalRows = filteredData.size();
                    if (totalRows == 0) {
                        return null;
                    }

                    int rowNum = 1;
                    for (Object[] rowData : filteredData) {
                        Row row = sheet.createRow(rowNum);
                        for (int colNum = 0; colNum < headers.length; colNum++) {
                            Cell cell = row.createCell(colNum);
                            cell.setCellStyle(borderedStyle);
                            cell.setCellValue(rowData[colNum] != null ? rowData[colNum].toString() : "");
                        }

                        int progress = (int) ((rowNum * 100.0) / totalRows);
                        publish(progress);
                        rowNum++;
                    }

                    for (int i = 0; i < headers.length; i++) {
                        sheet.autoSizeColumn(i);
                    }

                    try (FileOutputStream fileOut = new FileOutputStream(path)) {
                        workbook.write(fileOut);
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "檔案寫入發生錯誤:\n(請檢查檔案是否被其他程式如 Excel 開啟中)\n" + e.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                progressBar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                if (progressBar.getValue() == 0 && resultTable.getRowCount() > 0) {
                    JOptionPane.showMessageDialog(null, "匯出完成，但在您設定的日期區間內沒有符合的結案資料。", "提示", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, "Excel 匯出成功！\n位置：" + path, "完成", JOptionPane.INFORMATION_MESSAGE);
                    try {
                        Desktop.getDesktop().open(fileChooser.getCurrentDirectory());
                    } catch (IOException ex) {
                        Logger.getLogger(ExportExcelTool.class.getName()).log(Level.SEVERE, null, ex);
                    }
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

    private LocalDate parseDateSafe(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    private List<Object[]> filterAndSortData(DefaultTableModel model, LocalDate startDate, LocalDate endDate) {
        List<Object[]> filteredList = new ArrayList<>();
        DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < model.getRowCount(); i++) {
            String status = model.getValueAt(i, 3).toString();
            String dateStr = model.getValueAt(i, 8).toString();

            if (!"完成".equals(status) && !"結案".equals(status)) {
                continue;
            }

            try {
                LocalDate rowDate = LocalDateTime.parse(dateStr, formatterDateTime).toLocalDate();
                if (startDate != null && rowDate.isBefore(startDate)) continue;
                if (endDate != null && rowDate.isAfter(endDate)) continue;
            } catch (Exception e) {
                continue;
            }

            String caseIdStr = model.getValueAt(i, 0).toString();
            String categoryId = model.getValueAt(i, 2).toString();
            String repairUnit = model.getValueAt(i, 5).toString();
            String extension = model.getValueAt(i, 6).toString();
            String engineer = model.getValueAt(i, 7).toString();
            String uuid = model.getValueAt(i, 9).toString();

            String category = categoryId;
            String categoryName = "";
            int catSpaceIdx = categoryId.indexOf(" ");
            if (catSpaceIdx != -1) {
                category = categoryId.substring(0, catSpaceIdx);
                categoryName = categoryId.substring(catSpaceIdx + 1);
            }

            String callUnit = repairUnit;
            int unitSlashIdx = repairUnit.indexOf("/");
            if (unitSlashIdx != -1) {
                callUnit = repairUnit.substring(0, unitSlashIdx);
            }

            String reportPerson = extension;
            String extensionNum = "";
            int extSpaceIdx = extension.indexOf(" ");
            if (extSpaceIdx != -1) {
                reportPerson = extension.substring(0, extSpaceIdx);
                extensionNum = extension.substring(extSpaceIdx + 1);
            }

            String engineerName = engineer;
            int engSpaceIdx = engineer.indexOf(" ");
            if (engSpaceIdx != -1) {
                engineerName = engineer.substring(engSpaceIdx + 1); // 取名字部分
            }

            String[] details = fetchTool.getCaseDetails(uuid);

            Object[] rowData = new Object[18];
            rowData[0] = caseIdStr;
            rowData[1] = category;
            rowData[2] = categoryName;
            rowData[3] = "國防醫學院";
            rowData[4] = callUnit;
            rowData[5] = reportPerson;
            rowData[6] = extensionNum;
            rowData[7] = dateStr;
            rowData[8] = details[0]; // arrivalTime
            rowData[9] = details[1]; // completionTime
            rowData[10] = details[2]; // issueDescription
            rowData[11] = details[3]; // maintenanceContent
            rowData[12] = "公司";
            rowData[13] = engineerName;
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