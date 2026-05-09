package smartfarm.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import smartfarm.model.SystemLog;
import smartfarm.service.SystemLogManager;
import java.io.*;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class LogsController {
    @FXML private Label lblTotal, lblInfo, lblWarnings, lblErrors;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbType;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<SystemLog> logTable;
    @FXML private TableColumn<SystemLog, String> colTimestamp, colType, colSource, colMessage, colUser;
    @FXML private Button btnExport, btnClear;

    private final SystemLogManager mgr = SystemLogManager.getInstance();

    @FXML public void initialize() {
        colTimestamp.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getFormattedTimestamp()));
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getType().name()));
        colSource.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSource()));
        colMessage.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getMessage()));
        colUser.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getUser()));
        colType.setCellFactory(col -> new TableCell<SystemLog, String>() {
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(item);
                    setStyle(switch(item) { case "INFO"->"-fx-text-fill:#1d4ed8;"; case "WARNING"->"-fx-text-fill:#d97706;"; default->"-fx-text-fill:#dc2626;"; } + "-fx-font-weight:bold;");
                }
            }
        });
        cmbType.getItems().addAll("All","INFO","WARNING","ERROR"); cmbType.setValue("All");
        cmbType.setOnAction(e->refresh()); txtSearch.textProperty().addListener((o,ov,nv)->refresh());
        dpFrom.valueProperty().addListener((o,ov,nv)->refresh()); dpTo.valueProperty().addListener((o,ov,nv)->refresh());
        refresh();
    }

    private void refresh() {
        List<SystemLog> logs = mgr.getLogs();
        String s = txtSearch.getText()!=null?txtSearch.getText().toLowerCase().trim():"";
        String t = cmbType.getValue(); LocalDate f=dpFrom.getValue(), to=dpTo.getValue();
        List<SystemLog> filtered = logs.stream()
                .filter(l->s.isEmpty()||l.getMessage().toLowerCase().contains(s)||l.getSource().toLowerCase().contains(s)||l.getUser().toLowerCase().contains(s))
                .filter(l->"All".equals(t)||l.getType().name().equals(t))
                .filter(l->f==null||!l.getTimestamp().toLocalDate().isBefore(f))
                .filter(l->to==null||!l.getTimestamp().toLocalDate().isAfter(to))
                .collect(Collectors.toList());
        logTable.setItems(FXCollections.observableArrayList(filtered));
        long info=logs.stream().filter(l->l.getType()==SystemLog.LogType.INFO).count();
        long warn=logs.stream().filter(l->l.getType()==SystemLog.LogType.WARNING).count();
        long err=logs.stream().filter(l->l.getType()==SystemLog.LogType.ERROR).count();
        lblTotal.setText(String.valueOf(logs.size())); lblInfo.setText(String.valueOf(info));
        lblWarnings.setText(String.valueOf(warn)); lblErrors.setText(String.valueOf(err));
    }

    @FXML private void onExport() {
        FileChooser fc=new FileChooser(); fc.setTitle("Export Logs");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV","*.csv"));
        fc.setInitialFileName("system_logs.csv");
        File f=fc.showSaveDialog(btnExport.getScene().getWindow()); if(f==null)return;
        try(FileWriter w=new FileWriter(f)) {
            w.write("Timestamp,Type,Source,Message,User\n");
            for(SystemLog l:logTable.getItems())
                w.write(String.format("%s,%s,%s,\"%s\",%s\n",l.getFormattedTimestamp(),l.getType(),l.getSource(),l.getMessage(),l.getUser()));
            alert("Done","Exported to "+f.getName(),Alert.AlertType.INFORMATION);
        }catch(IOException e){alert("Error",e.getMessage(),Alert.AlertType.ERROR);}
    }

    @FXML private void onClear() {
        Alert c=new Alert(Alert.AlertType.CONFIRMATION,"Clear all logs?",ButtonType.YES,ButtonType.NO);
        c.showAndWait().ifPresent(b->{if(b==ButtonType.YES){mgr.clear();refresh();}});
    }

    private void alert(String t,String m,Alert.AlertType ty){new Alert(ty,m,ButtonType.OK){{setTitle(t);}}.showAndWait();}
}