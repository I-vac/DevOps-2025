<#import "layout.ftl" as layout>
<@layout.layout title="Sign Up">
  <h2>Sign Up</h2>
  <#if error??>
    <div class="error"><strong>Error:</strong> ${error}</div>
  </#if>
  <form action="/register" method="post">
    <dl>
      <dt>Username:</dt>
      <dd><input type="text" name="username" size="30" value="${(username)!}"></dd>
      <dt>E-Mail:</dt>
      <dd><input type="text" name="email" size="30" value="${(email)!}"></dd>
      <dt>Password:</dt>
      <dd><input type="password" name="password" size="30"></dd>
      <dt>Password <small>(repeat)</small>:</dt>
      <dd><input type="password" name="password2" size="30"></dd>
    </dl>
    <div class="actions"><input type="submit" value="Sign Up"></div>
  </form>
</@layout.layout>