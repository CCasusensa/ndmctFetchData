package tool;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import top.gcszhn.d4ocr.OCREngine;

/**
 * @author K0dan
 */
public class LoginAndFetchTool extends JFrame {

    private String tokenValue = "";
    private Map<String, String> cookies = new HashMap<>();
    private boolean isLoggedIn = false;

    public LoginAndFetchTool() {
        initComponents();
        setupTable();

        setTitle("國防醫學院報修系統表格Dump Ver 3.0 (Optimized)");
        setLocationRelativeTo(null);
        setupSSL();
        loadCaptchaAndToken();
        setupSearchFilter();
    }

    private void setupTable() {
        for (int i = 9; i < 18; i++) {
            TableColumn column = resultTable.getColumnModel().getColumn(i);
            column.setMinWidth(0);
            column.setMaxWidth(0);
            column.setPreferredWidth(0);
        }

        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JTable currentTable = (JTable) e.getSource();
                int row = currentTable.getSelectedRow();
                if (row != -1) {
                    int modelRow = currentTable.convertRowIndexToModel(row);
                    String uuid = (String) currentTable.getModel().getValueAt(modelRow, 9);
                    fetchCaseDetails(uuid);
                }
            }
        });
    }

    private void setupSearchFilter() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String text = searchField.getText().trim();
                TableRowSorter<TableModel> rowSorter = (TableRowSorter<TableModel>) resultTable.getRowSorter();
                if (text.isEmpty()) {
                    rowSorter.setRowFilter(null);
                } else {
                    rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text), 0, 1, 3, 5, 6, 7));
                }
            }
        });
    }

    private void setupSSL() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCaptchaAndToken() {
        captchaLabel.setText("載入中...");
        new SwingWorker<ImageIcon, Void>() {
            private String predictCode = "";

            @Override
            protected ImageIcon doInBackground() {
                try {
                    Connection.Response indexRes = Jsoup.connect("https://fix.ndmctsgh.edu.tw/ndmc/index.php")
                            .method(Connection.Method.GET)
                            .execute();

                    cookies.putAll(indexRes.cookies());
                    Element tokenEl = indexRes.parse().selectFirst("input[name=token]");
                    tokenValue = (tokenEl != null) ? tokenEl.attr("value") : "";

                    String captchaUrl = "https://fix.ndmctsgh.edu.tw/ndmc/captcha.php?t=" + System.currentTimeMillis();
                    Connection.Response captchaRes = Jsoup.connect(captchaUrl)
                            .cookies(cookies)
                            .ignoreContentType(true)
                            .execute();

                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(captchaRes.bodyAsBytes()));

                    if (image != null) {
                        BufferedImage cleanImage = preprocessCaptcha(image);
                        predictCode = OCREngine.instance().recognize(cleanImage);
                        return new ImageIcon(cleanImage.getScaledInstance(150, 50, Image.SCALE_SMOOTH));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon captchaImage = get();
                    if (captchaImage != null) {
                        captchaLabel.setIcon(captchaImage);
                        captchaLabel.setText(null);
                        jTextField3.setText(predictCode);
                    } else {
                        captchaLabel.setIcon(null);
                        captchaLabel.setText("驗證碼載入失敗");
                    }
                } catch (Exception e) {
                    captchaLabel.setText("驗證碼載入失敗");
                }
            }
        }.execute();
    }

    private BufferedImage preprocessCaptcha(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x < 1 || x >= width - 1 || y < 1 || y >= height - 1) {
                    finalImage.setRGB(x, y, Color.WHITE.getRGB());
                    continue;
                }
                int rgb = original.getRGB(x, y);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;

                if (gray < 160) {
                    finalImage.setRGB(x, y, Color.BLACK.getRGB());
                } else {
                    finalImage.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }
        return finalImage;
    }

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {
        jButton1.setEnabled(false);
        summaryTextArea.setText("開始登入程序...");

        new SwingWorker<Boolean, Void>() {
            private String finalMessage = "";

            @Override
            protected Boolean doInBackground() {
                int maxRetries = 5;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        String loginUrl = "https://fix.ndmctsgh.edu.tw/ndmc/auth/loginProcess.php";
                        Connection.Response res = Jsoup.connect(loginUrl)
                                .cookies(cookies)
                                .data("username", jTextField1.getText())
                                .data("password", jTextField2.getText())
                                .data("verifycode", jTextField3.getText())
                                .data("token", tokenValue)
                                .method(Connection.Method.POST)
                                .execute();

                        String serverResponse = res.body().trim();
                        if ("ok".equalsIgnoreCase(serverResponse)) {
                            finalMessage = "登入成功！(於第 " + attempt + " 次嘗試)";
                            return true;
                        }

                        finalMessage = "第 " + attempt + " 次失敗 (" + serverResponse + ")，自動重試中...";
                        SwingUtilities.invokeLater(() -> summaryTextArea.setText(finalMessage));

                        Connection.Response indexRes = Jsoup.connect("https://fix.ndmctsgh.edu.tw/ndmc/index.php").execute();
                        cookies.putAll(indexRes.cookies());
                        Element tokenEl = indexRes.parse().selectFirst("input[name=token]");
                        tokenValue = (tokenEl != null) ? tokenEl.attr("value") : "";

                        Connection.Response captchaRes = Jsoup.connect("https://fix.ndmctsgh.edu.tw/ndmc/captcha.php?t=" + System.currentTimeMillis())
                                .cookies(cookies).ignoreContentType(true).execute();
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(captchaRes.bodyAsBytes()));
                        
                        if (image != null) {
                            BufferedImage cleanImg = preprocessCaptcha(image);
                            String newPredict = OCREngine.instance().recognize(cleanImg);
                            SwingUtilities.invokeLater(() -> {
                                jTextField3.setText(newPredict);
                                captchaLabel.setIcon(new ImageIcon(cleanImg.getScaledInstance(150, 50, Image.SCALE_SMOOTH)));
                            });
                        }
                        Thread.sleep(500);
                    } catch (Exception e) {
                        finalMessage = "發生網路錯誤：" + e.getMessage();
                    }
                }
                return false;
            }

            @Override
            protected void done() {
                jButton1.setEnabled(true);
                try {
                    if (get()) {
                        isLoggedIn = true;
                        JOptionPane.showMessageDialog(LoginAndFetchTool.this, finalMessage, "成功", JOptionPane.INFORMATION_MESSAGE);
                        summaryTextArea.setText(finalMessage);
                        fetchRepairCases();
                    } else {
                        JOptionPane.showMessageDialog(LoginAndFetchTool.this, "連續失敗 5 次，請確認帳密是否正確。", "錯誤", JOptionPane.ERROR_MESSAGE);
                        summaryTextArea.setText("已連續失敗 5 次，登入中止。");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void fetchRepairCases() {
        summaryTextArea.setText("正在載入案件資料...");
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                Document document = Jsoup.connect("https://fix.ndmctsgh.edu.tw/ndmc/home.php")
                        .cookies(cookies).get();

                List<Object[]> rowsData = new ArrayList<>();
                Elements rows = document.select(".body-container table tbody tr");
                for (Element row : rows) {
                    Elements links = row.select("td a[href*=casedetail2.php?uuid=]");
                    if (!links.isEmpty()) {
                        Elements data = row.select("td");
                        rowsData.add(new Object[]{
                            data.get(0).text(), data.get(1).text().replace("[更多...]", ""),
                            data.get(2).text(), data.get(3).text(), data.get(4).text(),
                            data.get(6).text(), data.get(7).text(), data.get(8).text(),
                            data.get(9).text(), links.attr("href").split("=")[1]
                        });
                    }
                }
                return rowsData;
            }

            @Override
            protected void done() {
                try {
                    List<Object[]> rowsData = get();
                    DefaultTableModel model = (DefaultTableModel) resultTable.getModel();
                    model.setRowCount(0);
                    if (rowsData.isEmpty()) {
                        summaryTextArea.setText("沒有找到維修案件");
                    } else {
                        for (Object[] rowData : rowsData) model.addRow(rowData);
                        summaryTextArea.setText("案件載入完成，共 " + rowsData.size() + " 筆。");
                    }
                } catch (Exception e) {
                    summaryTextArea.setText("載入案件失敗");
                }
            }
        }.execute();
    }

    private void fetchCaseDetails(String uuid) {
        summaryTextArea.setText("正在載入案件明細...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                Document document = Jsoup.connect("https://fix.ndmctsgh.edu.tw/ndmc/casedetail2.php?uuid=" + uuid)
                        .cookies(cookies).get();

                String caseId = "", caseCode = "", caseIndex = "", repairContent = "", caseStatus = "", user = "", engineer = "";
                String arrivalTime = "", endTime = "", repairDetail = "", finalStatus = "";

                for (Element row : document.select(".table tbody tr")) {
                    Elements cols = row.select("td");
                    if (cols.size() >= 2) {
                        String key = cols.get(0).text().trim();
                        String value = cols.get(1).text().trim();
                        switch (key) {
                            case "維修單號:": caseId = value; break;
                            case "申請種類:": caseCode = value; break;
                            case "申請/故障派修類別:": caseIndex = value; break;
                            case "報修內容:": repairContent = value; break;
                            case "使用者資訊:": user = value; break;
                            case "維護廠商/工程師:": engineer = value; break;
                            case "目前案件狀態:": caseStatus = value; break;
                        }
                    }
                    if (cols.size() == 4 && !cols.get(0).text().contains("000102030405")) {
                        arrivalTime = cols.get(0).text().trim();
                        endTime = cols.get(1).text().trim();
                        repairDetail = cols.get(2).text().trim();
                        finalStatus = cols.get(3).text().trim();
                    }
                }
                return "**案件明細**\n"
                        + "**案件編號:** " + (caseId.isEmpty() ? "無資料" : caseId) + "\n"
                        + "**申請種類:** " + (caseCode.isEmpty() ? "無資料" : caseCode) + "\n"
                        + "**申請/故障類別:** " + (caseIndex.isEmpty() ? "無資料" : caseIndex) + "\n"
                        + "**報修內容:** " + (repairContent.isEmpty() ? "無資料" : repairContent) + "\n"
                        + "**使用者資訊:** " + (user.isEmpty() ? "無資料" : user) + "\n"
                        + "**維修工程師:** " + (engineer.isEmpty() ? "未分配" : engineer) + "\n"
                        + "**案件狀態:** " + (caseStatus.isEmpty() ? "無資料" : caseStatus) + "\n\n"
                        + "**維修記錄**\n"
                        + "**到場時間:** " + (arrivalTime.isEmpty() ? "無資料" : arrivalTime) + "\n"
                        + "**結束時間:** " + (endTime.isEmpty() ? "無資料" : endTime) + "\n"
                        + "**維修內容:** " + (repairDetail.isEmpty() ? "無資料" : repairDetail) + "\n"
                        + "**修復後狀態:** " + (finalStatus.isEmpty() ? "無資料" : finalStatus);
            }

            @Override
            protected void done() {
                try {
                    summaryTextArea.setText(get());
                    summaryTextArea.setCaretPosition(0);
                } catch (Exception e) {
                    summaryTextArea.setText("取得詳細資料發生錯誤");
                }
            }
        }.execute();
    }

    public String[] getCaseDetails(String uuid) {
        try {
            Document document = Jsoup.connect("https://fix.ndmctsgh.edu.tw/ndmc/casedetail2.php?uuid=" + uuid)
                    .cookies(cookies).get();
            String arrivalTime = "", endTime = "", issueDescription = "", maintenanceContent = "";

            for (Element row : document.select(".table tbody tr")) {
                Elements cols = row.select("td");
                if (cols.size() >= 2 && cols.get(0).text().contains("報修內容:")) {
                    issueDescription = cols.get(1).text().trim();
                }
                if (cols.size() == 4 && !cols.get(0).text().contains("000102030405")) {
                    arrivalTime = cols.get(0).text().trim();
                    endTime = cols.get(1).text().trim();
                    maintenanceContent = cols.get(2).text().trim();
                }
            }
            return new String[]{
                arrivalTime.isEmpty() ? "無資料" : arrivalTime,
                endTime.isEmpty() ? "無資料" : endTime,
                issueDescription.isEmpty() ? "無資料" : issueDescription,
                maintenanceContent.isEmpty() ? "無資料" : maintenanceContent
            };
        } catch (Exception e) {
            return new String[]{"錯誤", "錯誤", "錯誤", "錯誤"};
        }
    }

    public String getStartDateText() { return jTextField4.getText(); }
    public String getEndDateText() { return jTextField5.getText(); }

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {
        if (isLoggedIn) {
            new ExportExcelTool(resultTable, this).exportToExcel();
        } else {
            JOptionPane.showMessageDialog(this, "請先登入!", "錯誤", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {
        if (isLoggedIn) {
            fetchRepairCases();
        } else {
            JOptionPane.showMessageDialog(this, "請先登入!", "錯誤", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {
        loadCaptchaAndToken();
    }
    
    @SuppressWarnings("unchecked")
    private void initComponents() {
        jPanel1 = new javax.swing.JPanel();
        captchaLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        summaryTextArea = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        resultTable = new javax.swing.JTable();
        jPanel2 = new javax.swing.JPanel();
        searchField = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jTextField5 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jButton4 = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jTextField2 = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jTextField3 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        captchaLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        summaryTextArea.setColumns(20);
        summaryTextArea.setRows(5);
        jScrollPane1.setViewportView(summaryTextArea);

        resultTable.setAutoCreateRowSorter(true);
        resultTable.setModel(new javax.swing.table.DefaultTableModel(
                new Object[][]{},
                new String[]{
                    "案號", "案件說明", "派修類別", "狀態", "指派人", "地點", "提出者 / 電話", "廠商 / 工程師", "提出時間", "單號", "", "", "", "", "", "", "", ""
                }
        ) {
            boolean[] canEdit = new boolean[]{
                true, true, true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit[columnIndex];
            }
        });
        resultTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        jScrollPane2.setViewportView(resultTable);
        if (resultTable.getColumnModel().getColumnCount() > 0) {
            resultTable.getColumnModel().getColumn(10).setResizable(false);
            resultTable.getColumnModel().getColumn(11).setResizable(false);
            resultTable.getColumnModel().getColumn(12).setResizable(false);
            resultTable.getColumnModel().getColumn(13).setResizable(false);
            resultTable.getColumnModel().getColumn(14).setResizable(false);
            resultTable.getColumnModel().getColumn(15).setResizable(false);
            resultTable.getColumnModel().getColumn(16).setResizable(false);
            resultTable.getColumnModel().getColumn(17).setResizable(false);
        }

        jLabel4.setText("搜索關鍵字:");
        jTextField5.setText("2025-03-31");
        jTextField4.setText("2025-03-01");
        jLabel5.setText("倒出表格日期間格:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
                jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel2Layout.createSequentialGroup()
                                .addContainerGap(64, Short.MAX_VALUE)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                                .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(40, 40, 40)
                                                .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(20, 20, 20))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                                .addComponent(searchField, javax.swing.GroupLayout.PREFERRED_SIZE, 191, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(84, 84, 84))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                                .addComponent(jLabel4)
                                                .addGap(143, 143, 143))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                                .addComponent(jLabel5)
                                                .addGap(117, 117, 117))))
        );
        jPanel2Layout.setVerticalGroup(
                jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(searchField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 11, Short.MAX_VALUE)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap())
        );

        jButton4.setText("倒出表格");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jLabel1.setText("帳號:");
        jTextField1.setText("");
        jLabel2.setText("密碼:");
        jTextField2.setText("");
        jLabel3.setText("驗證碼:");

        jButton1.setText("登入");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setText("刷新驗證碼");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton3.setText("刷新案件");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
                jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(jLabel1)
                                        .addComponent(jLabel2)
                                        .addComponent(jLabel3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(jTextField3)
                                        .addComponent(jTextField2)
                                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
                jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel3Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addGroup(jPanel3Layout.createSequentialGroup()
                                                .addComponent(jButton1)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jButton2)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jButton3))
                                        .addGroup(jPanel3Layout.createSequentialGroup()
                                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(jLabel2))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(jLabel3))))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 1583, Short.MAX_VALUE)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                .addComponent(captchaLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                .addGap(83, 83, 83))
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                                                .addGap(0, 0, Short.MAX_VALUE)
                                                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 752, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(192, 192, 192)))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                                                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addContainerGap())
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                                                .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(140, 140, 140))
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                                                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(44, 44, 44))))))
        );
        jPanel1Layout.setVerticalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(captchaLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGap(99, 99, 99)
                                                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addContainerGap()
                                                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(5, 5, 5)
                                                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGap(14, 14, 14)
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 505, Short.MAX_VALUE)
                                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addContainerGap())
        );

        pack();
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> {
            new LoginAndFetchTool().setVisible(true);
        });
    }

    private javax.swing.JLabel captchaLabel;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTable resultTable;
    private javax.swing.JTextField searchField;
    private javax.swing.JTextArea summaryTextArea;
}