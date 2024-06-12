<!-- Include Chart.js library -->
<script src="${request.contextPath}/plugin/org.joget.marketplace.ChartJs/chart.umd.min.js"></script>
<script src="${request.contextPath}/plugin/org.joget.marketplace.ChartJs/chartjs.plugin.datalabels.js"></script>
<script src="${request.contextPath}/plugin/org.joget.marketplace.ChartJs/html2canvas.min.js"></script>

<script src="${request.contextPath}/plugin/org.joget.marketplace.ChartJs/jspdf.umd.min.js"></script>

<div class="chartjs_menu_body">

    <#if element.properties.error! != "">
        ${element.properties.error!}
    </#if>

    <!-- Chart Container -->
    <#if element.properties.labels! != "" && element.properties.datasets! != "">
    <div id="chart-container-${element.properties.id!}" style="position: relative; height: ${element.properties.chartHeight!}; max-width: ${element.properties.chartWidth!}">
        <canvas id="chart-${element.properties.id!}"></canvas>
    
</div>
    </#if>

    <#if element.properties.buttons! == "true">
    <div class="chartjs-buttons">
        <button class="btn-download btn-pdf" onclick="downloadPDF()">Download PDF</button>
        <button class="btn-download btn-csv" onclick="downloadCSV()">Download CSV</button>
    </div>
    </#if>


    <div id="chartjs-body-${element.properties.id!}" class="chart-body-content">
    </div>

    <style type="text/css">

          .chartjs-buttons {
            display: flex;
            justify-content: center;
            margin-top: 1.2em; 
            gap: 1.5em; 
        }

        .btn-download {
            padding: 0.3em 0.5em; 
            margin-bottom: 0.5em;
            border: 0.125em solid transparent;
            border-radius: 0.3125em;
            font-size: 0.875em;
            cursor: pointer;
            transition: background-color 0.3s, box-shadow 0.3s, border-color 0.3s;
        }

        .btn-pdf {
            background-color: #4CAF50;
            color: white;
        }

        .btn-csv {
            background-color: #2196F3;
            color: white;
        }

        .btn-download:hover {
            box-shadow: 0 0.25em 0.5em rgba(0, 0, 0, 0.2);
            border-color: #000;
        }

        .btn-pdf:hover {
            background-color: #45a049;
        }

        .btn-csv:hover {
            background-color: #1e88e5;
        }


        <#if element.properties.showTable! != "bottom" && element.properties.showTable! != "top">
        #chartjs-body-${element.properties.id!} .dataList .table-wrapper, #chartjs-datalist-${element.properties.id!} .pagebanner, #chartjs-datalist-${element.properties.id!} .pagelinks, #chartjs-datalist-${element.properties.id!} .exportlinks {
            display: none !important;
        }
        </#if>
        <#if element.properties.showFilter! != "true">
        #chartjs-body-${element.properties.id!} .filter_form{
            display: none !important;
        }
        </#if>
        <#if element.properties.showExportLinks! != "true">
        #chartjs-body-${element.properties.id!} .exportlinks{
            display: none !important;
        }
        </#if>

        /* remove border in progressive theme*/
        body:not(#login) #content > main > .datalist-body-content:not(.quickEdit) {
            border: none;
            box-shadow: none;
        }
    </style>

    <#if element.properties.labels! != "" && element.properties.datasets! != "">

    <script type="text/javascript">
$(document).ready(function() {
   
    var ctx = document.getElementById("chart-${element.properties.id!}").getContext('2d');

    // Function to parse numerical values from strings
 function parseNumberFromString(item) {
    if (item === null || item === undefined) {
        return NaN; // Return NaN if the input is null or undefined
    }

    var value = (typeof item === 'string') ? item : item.toString(); 

    value = value.replace(/[^\d.,-]/g, '').trim();

    var lastCommaIndex = value.lastIndexOf(',');
    var lastPeriodIndex = value.lastIndexOf('.');
    if (lastCommaIndex > lastPeriodIndex) {
        value = value.replace(/\./g, '').replace(/,/g, '.');
    } else {
        value = value.replace(/,/g, '');
    }

    var parsedValue = parseFloat(value);
    if (isNaN(parsedValue)) {
        return NaN; // Return NaN if the conversion fails
    }
    return parsedValue;
}


    var rawChartData = {
        labels: ${element.properties.labels!},
        datasets: ${element.properties.datasets!}
    };


    rawChartData.datasets.forEach(function (dataset) {
        dataset.data = dataset.data.map(parseNumberFromString);
    });


    var chartOptions = JSON.parse("${element.properties.options!?json_string}");

    myChart = new Chart(ctx, {
        type: '${element.properties.chartType!}',
        data: rawChartData,
        options: chartOptions,
        plugins: [ChartDataLabels]
    });

    $(window).off("resize.chart-${element.properties.id}");
    $(window).on("resize.chart-${element.properties.id}", function () {
        if ($("#chart-${element.properties.id!}").data("chart")) {
            $("#chart-${element.properties.id!}").css({
                width: "100%",
                height: "100%"
            });
            myChart.resize();
        } else {
            $(window).off("resize.chart-${element.properties.id}");
        }
    });

    <#if element.properties.showTable! == "bottom">
    $("#chartjs-body-${element.properties.id!} .dataList").detach().appendTo("#chartjs-body-${element.properties.id!}");
    </#if>

});

function downloadPDF() {
    const chartElement = document.getElementById('chart-container-${element.properties.id!}');
    html2canvas(chartElement, { scale: 2 }).then(canvas => {
        const imgData = canvas.toDataURL('image/png');
        const pdf = new jspdf.jsPDF('landscape');
        const pageWidth = pdf.internal.pageSize.getWidth();
        const pageHeight = pdf.internal.pageSize.getHeight();
        const imgWidth = pageWidth - 20; // Adding some padding
        const imgHeight = (canvas.height * imgWidth) / canvas.width;
        const yPos = (pageHeight - imgHeight) / 2; // Centering the image vertically
        pdf.addImage(imgData, 'PNG', 10, yPos, imgWidth, imgHeight);
        pdf.save('chart.pdf');
    });
}

function downloadCSV() {
    // Extract data from Chart.js
    var csv = 'Labels,Values\n';
    myChart.data.labels.forEach(function(label, index) {
        myChart.data.datasets.forEach(function(dataset) {
            csv += label + ',' + dataset.data[index] + '\n';
        });
    });

    var blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });

    var reader = new FileReader();
    reader.onload = function(event) {
        var link = document.createElement("a");
        link.href = event.target.result;
        link.download = "chart_data.csv";
        link.click();
    };
    reader.readAsDataURL(blob);
}


</script>

    </#if>

    ${element.properties.customChartFooter!}
</div>