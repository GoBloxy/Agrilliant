from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CSS_FILES = [
    ROOT / "src/main/resources/css/farm-theme.css",
    ROOT / "src/main/resources/css/mobile.css",
]
FXML_DIR = ROOT / "src/main/resources/fxml"

REQUIRED_FILES = [
    *CSS_FILES,
    FXML_DIR / "signin.fxml",
    FXML_DIR / "signup.fxml",
    FXML_DIR / "dashboard.fxml",
    FXML_DIR / "monitoring.fxml",
    FXML_DIR / "alerts.fxml",
    FXML_DIR / "crops.fxml",
    FXML_DIR / "plots.fxml",
    FXML_DIR / "workers.fxml",
    FXML_DIR / "tasks.fxml",
    FXML_DIR / "harvest.fxml",
    FXML_DIR / "reports.fxml",
    FXML_DIR / "logs.fxml",
]

REQUIRED_CSS_TOKENS = [
    "-ag-primary",
    "-ag-on-primary",
    "-ag-primary-container",
    "-ag-background",
    "-ag-surface",
    "-ag-surface-container",
    "-ag-on-surface",
    "-ag-outline",
    "-ag-error",
    "-ag-warning-container",
    "-ag-info-container",
]

REQUIRED_STYLE_CLASSES = [
    "page-header",
    "page-kicker",
    "page-title",
    "page-subtitle",
    "hero-card",
    "hero-title",
    "hero-subtitle",
    "section-card",
    "section-header",
    "summary-card",
    "metric-icon",
    "sensor-card",
    "list-card",
    "list-row-card",
    "btn-primary",
    "btn-secondary",
    "btn-tonal",
    "btn-danger",
    "search-box",
    "filter-combo",
    "filter-chip",
    "badge",
    "badge-high",
    "badge-warning",
    "badge-error",
    "chart-card",
    "empty-state",
    "auth-card",
    "drawer-header",
    "appbar-user-label",
]

REQUIRED_FX_IDS = {
    "signin.fxml": ["txtEmail", "txtPassword", "lblError", "btnSignIn", "btnFingerprint", "lblFpStatus"],
    "signup.fxml": ["txtFullName", "txtUsername", "txtEmail", "txtPhone", "txtPassword", "txtConfirm", "cmbRole", "lblError", "btnSignUp"],
    "alerts.fxml": ["rootPane", "listSection", "cmbSeverity", "cmbStatus", "txtSearch", "btnMarkAllRead", "alertTable", "detailPane", "btnCloseDetails"],
    "crops.fxml": ["listView", "btnExport", "btnAddCrop", "cropTable"],
    "dashboard.fxml": ["lblTemperature", "lblHumidity", "lblSoilMoisture", "alertListView", "taskListView", "cropTable", "workerTable", "harvestTable"],
    "harvest.fxml": ["btnRecordHarvest", "lblTotalHarvested", "lblTotalRevenue", "lblTotalCost", "lblNetProfit", "txtSearch", "cmbQuality", "harvestList"],
    "logs.fxml": ["btnExport", "btnClear", "lblTotal", "lblInfo", "lblWarnings", "lblErrors", "txtSearch", "cmbType", "dpFrom", "dpTo", "logList"],
    "monitoring.fxml": ["sensorTable"],
    "plots.fxml": ["plotTable"],
    "reports.fxml": ["btnExport", "lblTotalHarvest", "lblAvgYield", "lblRevenue", "lblProfitMargin", "yieldChart", "harvestTable"],
    "tasks.fxml": ["btnAddTask", "lblTotalTasks", "lblPending", "lblInProgress", "lblCompleted", "txtSearch", "cmbStatus", "taskList"],
    "workers.fxml": ["btnAddWorker", "lblTotalWorkers", "lblOnDuty", "lblAvailable", "lblBusy", "txtSearch", "cmbStatus", "workerList"],
}

missing_files = [str(path.relative_to(ROOT)) for path in REQUIRED_FILES if not path.exists()]
if missing_files:
    print("Missing required files:")
    for item in missing_files:
        print(f"  - {item}")
    sys.exit(1)

css = "\n".join(path.read_text(encoding="utf-8") for path in CSS_FILES)
missing_tokens = [token for token in REQUIRED_CSS_TOKENS if token not in css]
missing_classes = [klass for klass in REQUIRED_STYLE_CLASSES if f".{klass}" not in css]

errors = []
if missing_tokens:
    errors.append("Missing CSS tokens: " + ", ".join(missing_tokens))
if missing_classes:
    errors.append("Missing CSS classes: " + ", ".join(missing_classes))

fx_id_pattern = re.compile(r'fx:id="([^"]+)"')
for filename, ids in REQUIRED_FX_IDS.items():
    path = FXML_DIR / filename
    text = path.read_text(encoding="utf-8")
    present = set(fx_id_pattern.findall(text))
    missing = [fx_id for fx_id in ids if fx_id not in present]
    if missing:
        errors.append(f"{filename} missing fx:id values: " + ", ".join(missing))
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"{filename} is not well-formed XML: {exc}")

if errors:
    for error in errors:
        print(error)
    sys.exit(1)

print("UI resource verification passed")
