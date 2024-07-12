package org.joget.marketplace;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormat;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.userview.model.Userview;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.service.UserviewUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class ChartJs extends UserviewMenu implements PluginWebSupport {

    private DataList cacheDataList = null;
    private final static String MESSAGE_PATH = "messages/Chartjs";

    @Override
    public String getCategory() {
        return "Marketplace";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-chart-bar\"></i>";
    }

    @Override
    public boolean isHomePageSupported() {
        return true; 
    }

    @Override
    public String getDecoratedMenu() {
        return null; 
    }

    @Override
    public String getName() {
        return "ChartJS Menu";
    }

    @Override
    public String getVersion() {
        return "8.0.1";
    }

    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.Chartjs.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.Chartjs.pluginLabel", getClass().getName(), "messages/Chartjs");
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        LogUtil.info(getClass().getName(), "Fetching property options from: /properties/chartjs.json");
        return AppUtil.readPluginResource(getClassName(), "/properties/chartjs.json", null, true, MESSAGE_PATH);

    }

    protected DataList getDataList() throws BeansException {
        if (cacheDataList == null) {
            // get datalist
            ApplicationContext ac = AppUtil.getApplicationContext();
            AppService appService = (AppService) ac.getBean("appService");
            DataListService dataListService = (DataListService) ac.getBean("dataListService");
            DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
            String id = getPropertyString("datalistId");
            AppDefinition appDef = appService.getAppDefinition(getRequestParameterString("appId"), getRequestParameterString("appVersion"));
            DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(id, appDef);

            if (datalistDefinition != null) {
                cacheDataList = dataListService.fromJson(datalistDefinition.getJson());

                if (getPropertyString(Userview.USERVIEW_KEY_NAME) != null && getPropertyString(Userview.USERVIEW_KEY_NAME).trim().length() > 0) {
                    cacheDataList.addBinderProperty(Userview.USERVIEW_KEY_NAME, getPropertyString(Userview.USERVIEW_KEY_NAME));
                }
                if (getKey() != null && getKey().trim().length() > 0) {
                    cacheDataList.addBinderProperty(Userview.USERVIEW_KEY_VALUE, getKey());
                }

                cacheDataList.setActionPosition(getPropertyString("buttonPosition"));
                cacheDataList.setSelectionType(getPropertyString("selectionType"));
                cacheDataList.setCheckboxPosition(getPropertyString("checkboxPosition"));
            }
        }
        return cacheDataList;
    }

    @Override
    public String getRenderPage() {
        Map<String, Object> freeMarkerModel = new HashMap<>();
        freeMarkerModel.put("request", getRequestParameters());
        freeMarkerModel.put("element", this);

        // Retrieve and prepare the data list HTML content
        String datalistContent = "";
        datalistContent = getDatalistHTML();
        LogUtil.info(getClass().getName(), "Datalist Content: " + datalistContent);

        // Fetch and prepare data for Chart.js
        DataList datalist = getDataList();
        if (datalist != null && datalist.getRows().size() > 0) {
            getBinderData(datalist);
        } else {
            LogUtil.warn(getClass().getName(), "Data list is empty. Skipping chart data binding.");
            // Clear existing chart properties if no data is available
            setProperty("datasets", "");
            setProperty("labels", "");
            setProperty("options", "");
            setProperty("legends", "");
        }

        // Handling library version setup
        String libraryVersion = "";
        if (getPropertyString("libraryVersion") != null && !getPropertyString("libraryVersion").isEmpty()) {
            libraryVersion = getPropertyString("libraryVersion");
        } else {
            // Default version if none specified
            libraryVersion = "2.9.4";  
        }
        setProperty("libraryVersion", libraryVersion);

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        String content = pluginManager.getPluginFreeMarkerTemplate(freeMarkerModel, getClass().getName(), "/templates/Chartjs.ftl", MESSAGE_PATH);

        return "<div id=chartjs-body-" + getPropertyString("id") + " class=\"chart-body-content\">" + datalistContent + content + "</div>";
    }

    protected String getDatalistHTML() {
        Map<String, Object> model = new HashMap<>();
        model.put("requestParameters", getRequestParameters());

        try {
            // Fetch the data list
            DataList dataList = getDataList();

            if (dataList != null) {
                // Handle results and possible redirections or alerts from data list actions
                DataListActionResult ac = dataList.getActionResult();
                if (ac != null) {
                    if (ac.getMessage() != null && !ac.getMessage().isEmpty()) {
                        setAlertMessage(ac.getMessage());
                    }
                    if (ac.getType() != null && DataListActionResult.TYPE_REDIRECT.equals(ac.getType())
                            && ac.getUrl() != null && !ac.getUrl().isEmpty()) {
                        // Handling redirection
                        if ("REFERER".equals(ac.getUrl())) {
                            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
                            if (request != null && request.getHeader("Referer") != null) {
                                setRedirectUrl(request.getHeader("Referer"));
                            } else {
                                setRedirectUrl("REFERER");
                            }
                        } else {
                            if (ac.getUrl().startsWith("?")) {
                                setRedirectUrl(getUrl() + ac.getUrl());
                            } else {
                                setRedirectUrl(ac.getUrl());
                            }
                        }
                    }
                }

                // set data list
                setProperty("dataList", dataList);
            } else {
                setProperty("error", "Data List \"" + getPropertyString("datalistId") + "\" not exist.");
            }
        } catch (BeansException ex) {
            StringWriter out = new StringWriter();
            ex.printStackTrace(new PrintWriter(out));
            String message = ex.toString();
            message += "\r\n<pre class=\"stacktrace\">" + out.getBuffer() + "</pre>";
            setProperty("error", message);
        }

        Map properties = getProperties();
        model.put("properties", properties);

        String result = UserviewUtil.renderJspAsString("userview/plugin/datalist.jsp", model);
        LogUtil.info(getClass().getName(), "Generated DataList HTML: " + result);
        return result;
    }

    protected double parseAndValidateData(String value) {
        if (value == null || value.trim().isEmpty()) {
            LogUtil.info(getClass().getName(), "Received null or empty string, returning default value 0.0");
            return 0.0;  // Default value if data is null or empty
        }
        try {
            value = value.trim().replace(",", "");
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LogUtil.error(getClass().getName(), e, "Failed to parse number from value: " + value);
            return 0.0;  // Default value if parsing fails
        }
    }

    protected void getBinderData(DataList datalist) {
        DataListCollection binderdata;
        DataListColumn[] columns;

        try {
            if (getPropertyString("chartUseAllDataRows").equalsIgnoreCase("true")) {
                binderdata = datalist.getRows(datalist.getTotal(), 0);
            } else {
                binderdata = datalist.getRows();
            }

            columns = datalist.getColumns();
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Not able to retrieve data from binder");
            setProperty("error", ResourceBundleUtil.getMessage("userview.sqlchartmenu.error.invalidData"));
            return;
        }

        if (binderdata != null && !binderdata.isEmpty()) {
            try {
                JSONArray datasets = new JSONArray();
                JSONArray labels = new JSONArray();
                String chartType = getPropertyString("chartType");

                // Populate labels
                for (Object row : binderdata) {
                    String label = getBinderFormattedValue(datalist, row, getPropertyString("mapping_x"));
                    labels.put(label);
                    LogUtil.info(getClass().getName(), "Label added: " + label);
                }

                // Generate legends
                JSONArray legends = new JSONArray();
                for (DataListColumn column : columns) {
                    if (!getPropertyString("mapping_x").equalsIgnoreCase(column.getName())) {
                        legends.put(column.getLabel());
                    }
                }
                LogUtil.info(getClass().getName(), "Legends JSON: " + legends.toString());
                setProperty("legends", legends.toString());

                // Generate dynamic colors
                int dataLength = binderdata.size();
                ColorConfig colorConfig = getDynamicColors(dataLength);

                for (DataListColumn column : columns) {
                    if (!getPropertyString("mapping_x").equalsIgnoreCase(column.getName())) {
                        JSONObject dataset = new JSONObject();
                        JSONArray data = new JSONArray();

                        for (Object row : binderdata) {
                            String value = getBinderFormattedValue(datalist, row, column.getName());
                            Double validatedValue = parseAndValidateData(value);  // Use 'value' directly here
                            data.put(validatedValue);
                            LogUtil.info(getClass().getName(), "Validated value added for column '" + column.getName() + "': " + validatedValue);
                        }

                        dataset.put("label", column.getLabel());
                        dataset.put("data", data);
                        colorConfig = getDynamicColors(dataLength);

                        // Determine which color set to use
                        List<String> selectedBackgroundColors;
                        if (!"true".equalsIgnoreCase(getPropertyString("opaqueChart"))) {
                            selectedBackgroundColors = colorConfig.backgroundColors;  // Use semi-transparent colors
                        } else {
                            selectedBackgroundColors = colorConfig.transparentBackgroundColors;  // Use opaque colors
                        }

                        //assign the selected colors to the dataset
                        dataset.put("backgroundColor", selectedBackgroundColors);
                        dataset.put("borderColor", colorConfig.borderColors);
                        dataset.put("borderWidth", 1);

                        // Set additional properties based on chart type
                        switch (chartType.toLowerCase()) {
                            case "line":
                                dataset.put("fill", false);
                                dataset.put("tension", 0.4); // For smooth curves

                                break;
                            case "area":
                                dataset.put("type", "line");

                                dataset.put("fill", true);
                                break;
                            case "bar":
                                dataset.put("fill", false);
                                break;
                            case "pie":
                                dataset.put("type", "pie");

                                break;
                            case "donut":
                                dataset.put("type", "doughnut");

                                dataset.put("cutout", "50%"); // Set cutout percentage to create a visible doughnut effect

                                break;
                            case "polarArea":
                                dataset.put("type", "polarArea");

                                break;
                            case "radar":
                                dataset.put("type", "radar");

                                break;
                            case "bubble":
                                dataset.put("type", "bubble");

                                break;
                            case "scatter":
                                dataset.put("type", "scatter");

                                break;
                        }

                        datasets.put(dataset);
                    }
                }

                // Log the final JSON objects
                LogUtil.info(getClass().getName(), "Datasets JSON: " + datasets.toString());
                LogUtil.info(getClass().getName(), "Labels JSON: " + labels.toString());

                // Set properties to be used in FTL
                setProperty("datasets", datasets.toString());
                setProperty("labels", labels.toString());

                // Construct the options object
                JSONObject options = new JSONObject();
                options.put("responsive", true);
                options.put("maintainAspectRatio", false);

                //Animation for line chart
                // Define animations JSON object if conditions are met
                JSONObject animations = null;
                if ("line".equalsIgnoreCase(chartType) && "true".equalsIgnoreCase(getPropertyString("enableAnimation"))) {
                    animations = new JSONObject();
                    JSONObject tensionAnimation = new JSONObject();
                    tensionAnimation.put("duration", 1000);
                    tensionAnimation.put("easing", "linear");
                    tensionAnimation.put("from", 1);
                    tensionAnimation.put("to", 0);
                    tensionAnimation.put("loop", true);
                    animations.put("tension", tensionAnimation);
                }

                // Integrate animations settings into the options object if applicable
                if (animations != null) {
                    options.put("animations", animations);
                }

                // Add plugins
                JSONObject plugins = new JSONObject();
                JSONObject legend = new JSONObject();
                legend.put("display", getPropertyString("showLegend").equalsIgnoreCase("true"));
                legend.put("position", "top");
                plugins.put("legend", legend);
                //tooltip
                if ("true".equalsIgnoreCase(getPropertyString("showToolTip"))) {
                    JSONObject tooltip = new JSONObject();
                    tooltip.put("enabled", true);
                    JSONObject callbacks = new JSONObject();
                    callbacks.put("label", "function(context) { return context.dataset.label + ': ' + context.parsed.y; }");
                    tooltip.put("callbacks", callbacks);
                    plugins.put("tooltip", tooltip);
                }

                JSONObject title = new JSONObject();
                title.put("display", true);
                title.put("text", getPropertyString("title"));// use the charts.json file to get this dynamically,very simple->follow the exmaple later in x.put
                plugins.put("title", title);

                JSONObject datalabels = new JSONObject();
                datalabels.put("display", true);
                datalabels.put("align", "end");
                datalabels.put("anchor", "end");
                datalabels.put("formatter", "(value, context) => { return value; }");
                datalabels.put("color", "black");
                plugins.put("datalabels", datalabels);

                options.put("plugins", plugins);

                // Add scales
                JSONObject scales = new JSONObject();
                JSONObject x = new JSONObject();
                x.put("display", true);
                x.put("title", new JSONObject().put("display", true).put("text", getPropertyString("categoryAxisLabel"))); // use the charts.json file to get this dynamically
                scales.put("x", x);

                JSONObject y = new JSONObject();
                y.put("display", true);
                y.put("beginAtZero", true);
                y.put("title", new JSONObject().put("display", true).put("text", "Values"));
                scales.put("y", y);

                options.put("scales", scales);

                // Add layout
                JSONObject layout = new JSONObject();
                JSONObject padding = new JSONObject();
                padding.put("left", 10);
                padding.put("right", 10);
                padding.put("top", 10);
                padding.put("bottom", 10);
                layout.put("padding", padding);

                options.put("layout", layout);

                // Set options property
                setProperty("options", options.toString());

            } catch (Exception e) {
                LogUtil.error(getClass().getName(), e, "Not able to render chart data");
            }
        } else {
            setProperty("error", ResourceBundleUtil.getMessage("userview.processStatus.noData"));
        }
    }

    public ColorConfig getDynamicColors(int dataLength) {
        List<String> baseColors = Arrays.asList(
                "rgba(255, 99, 132, 0.2)",
                "rgba(54, 162, 235, 0.2)",
                "rgba(255, 206, 86, 0.2)",
                "rgba(75, 192, 192, 0.2)",
                "rgba(153, 102, 255, 0.2)",
                "rgba(255, 159, 64, 0.2)",
                "rgba(199, 199, 199, 0.2)",
                "rgba(83, 102, 255, 0.2)",
                "rgba(255, 129, 64, 0.2)",
                "rgba(129, 199, 132, 0.2)",
                "rgba(229, 99, 255, 0.2)",
                "rgba(139, 202, 86, 0.2)",
                "rgba(55, 192, 162, 0.2)",
                "rgba(203, 102, 132, 0.2)"
        );

        List<String> baseBorderColors = Arrays.asList(
                "rgba(255, 99, 132, 1)",
                "rgba(54, 162, 235, 1)",
                "rgba(255, 206, 86, 1)",
                "rgba(75, 192, 192, 1)",
                "rgba(153, 102, 255, 1)",
                "rgba(255, 159, 64, 1)",
                "rgba(199, 199, 199, 1)",
                "rgba(83, 102, 255, 1)",
                "rgba(255, 129, 64, 1)",
                "rgba(129, 199, 132, 1)",
                "rgba(229, 99, 255, 1)",
                "rgba(139, 202, 86, 1)",
                "rgba(55, 192, 162, 1)",
                "rgba(203, 102, 132, 1)"
        );
        List<String> transparentBackgroundColors = baseColors.stream()
                .map(color -> color.replace("0.2", "1")) // Convert semi-transparent colors to opaque
                .collect(Collectors.toList());

        List<String> backgroundColors = new ArrayList<>();
        List<String> borderColors = new ArrayList<>();
        List<String> opaqueColors = new ArrayList<>();

        for (int i = 0; i < dataLength; i++) {
            int colorIndex = i % baseColors.size();
            backgroundColors.add(baseColors.get(colorIndex));
            borderColors.add(baseBorderColors.get(colorIndex));
            opaqueColors.add(transparentBackgroundColors.get(colorIndex));

        }

        return new ColorConfig(backgroundColors, borderColors, opaqueColors);
    }

    private class ColorConfig {

        List<String> backgroundColors;
        List<String> borderColors;
        List<String> transparentBackgroundColors; // Added list for opaque background colors

        public ColorConfig(List<String> backgroundColors, List<String> borderColors, List<String> transparentBackgroundColors) {
            this.backgroundColors = backgroundColors;
            this.borderColors = borderColors;
            this.transparentBackgroundColors = transparentBackgroundColors;

        }
    }

    protected String getBinderFormattedValue(DataList dataList, Object o, String name) {
        DataListColumn[] columns = dataList.getColumns();
        for (DataListColumn c : columns) {
            if (c.getName().equalsIgnoreCase(name)) {
                String value;
                try {
                    value = DataListService.evaluateColumnValueFromRow(o, name).toString();
                    Collection<DataListColumnFormat> formats = c.getFormats();
                    if (formats != null) {
                        for (DataListColumnFormat f : formats) {
                            if (f != null) {
                                value = f.format(dataList, c, o, value);
                                return value;
                            } else {
                                return value;
                            }
                        }
                    } else {
                        return value;
                    }
                } catch (Exception ex) {

                }
            }
        }
        return "";
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String action = request.getParameter("action");
        if ("getDatalistColumns".equals(action)) {
            try {
                ApplicationContext ac = AppUtil.getApplicationContext();
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
                DataListService dataListService = (DataListService) ac.getBean("dataListService");

                String datalistId = request.getParameter("id");
                DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

                DataList datalist;
                if (datalistDefinition != null) {
                    datalist = dataListService.fromJson(datalistDefinition.getJson());
                    DataListColumn[] datalistcolumns = datalist.getColumns();

                    JSONArray columns = new JSONArray();
                    for (int i = 0; i < datalistcolumns.length; i++) {
                        JSONObject column = new JSONObject();
                        column.put("value", datalistcolumns[i].getName());
                        column.put("label", datalistcolumns[i].getLabel());
                        columns.put(column);
                    }
                    columns.write(response.getWriter());
                } else {
                    JSONArray columns = new JSONArray();
                    columns.write(response.getWriter());
                }

            } catch (IOException | JSONException | BeansException e) {
                LogUtil.error(getClass().getName(), e, "Webservice getColumns");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
