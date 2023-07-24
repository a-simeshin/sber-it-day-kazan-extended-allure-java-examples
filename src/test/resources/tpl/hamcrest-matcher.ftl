<#ftl output_format="HTML">
<#-- @ftlvariable name="data" type="ru.sber.it.day.AllureMatcherDto" -->

<head>
    <meta charset="UTF-8">
</head>

<style>
    .preformatted-text {
        background-color: #f7f7f7;
        border: 1px solid #ccc;
        border-radius: 0.25rem;
        font-family: Consolas, Menlo, Courier, monospace;
        font-size: 1rem;
        line-height: 1.5;
        padding: 1rem;
        margin-bottom: 2rem;
        overflow: auto;
    }
</style>

<h4>Reason</h4>
<div class="preformatted-text">
    <pre>${data.reason}</pre>
</div>

<h4>Description</h4>
<div class="preformatted-text">
    <pre>${data.expecting}</pre>
</div>

<h4>Actual</h4>
<div class="preformatted-text">
    <pre>${data.actual}</pre>
</div>