<#-- @ftlvariable name="routes" type="java.util.List<com.nyct.stopsequences.SequenceDebugger.RouteEntry>" -->
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Sequence Debug</title>
    <style type="text/css">
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        }

        .routeDiagram {
            display: flex;
            justify-content: center;
        }
    </style>
</head>
<body>

<h1>Sequence Debug</h1>
<ul>
    <#list routes as route>
        <li><a href="#${route.route.shortName}-${route.directionId}">${route.route.shortName} - ${route.route.longName}
                - Direction ${route.directionId}</a></li>
    </#list>
</ul>

<hr>

<#list routes as route>
    <div id="${route.route.shortName}-${route.directionId}">
        <h2>${route.route.shortName} - ${route.route.longName} - Direction ${route.directionId}</h2>
        <#list route.headsigns as headsign>
            <h3>${headsign}</h3>
        </#list>
        <div class="routeDiagram">${route.graphSvg.@@markup?no_esc}</div>
    </div>
    <hr>
</#list>

</body>
</html>
