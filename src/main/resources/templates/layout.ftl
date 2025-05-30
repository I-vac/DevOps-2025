<#macro layout title="Welcome">
<!doctype html>
<title><#if title??>${title}<#else>Welcome</#if> | MiniTwit</title>
<link rel="stylesheet" type="text/css" href="/style.css">
<div class="page">
  <h1>MiniTwit</h1>
  <div class="navigation">
    <#if user??>
      <a href="/">my timeline</a> |
      <a href="/public">public timeline</a> |
      <a href="/logout">sign out [${user.username?html}]</a>
    <#else>
      <a href="/public">public timeline</a> |
      <a href="/register">sign up</a> |
      <a href="/login">sign in</a>
    </#if>
  </div>
  <#if flashes??>
    <ul class="flashes">
      <#list flashes as message>
        <li>${message?html}</li>
      </#list>
    </ul>
  </#if>
  <div class="body">
    <#nested>
  </div>
  <div class="footer">
    MiniTwit &mdash; A Java Application
  </div>
</div>
</#macro>