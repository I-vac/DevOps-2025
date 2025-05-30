<#import "layout.ftl" as layout>
<@layout.layout title="Sign In">
  <h2>Sign In</h2>
  <#if error??>
    <div class="error"><strong>Error:</strong> ${error?html}</div>
  </#if>
  <form action="/login" method="post">
    <input type="hidden" name="csrf_token" value="${csrf_token?html}">
    <dl>
      <dt>Username:</dt>
      <dd><input type="text" name="username" size="30" value="${(username)!?html}"></dd>
      <dt>Password:</dt>
      <dd><input type="password" name="password" size="30"></dd>
    </dl>
    <div class="actions"><input type="submit" value="Sign In"></div>
  </form>
</@layout.layout>