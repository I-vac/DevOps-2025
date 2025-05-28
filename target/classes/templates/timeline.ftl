<#import "layout.ftl" as layout>
<@layout.layout>
  <#if title??>
    <h2>${title}</h2>
  <#else>
    <h2>My Timeline</h2>
  </#if>

  <#if user??>
    <#if profile_user??>
      <div class="followstatus">
        <#if user.user_id == profile_user.user_id>
          This is you!
        <#elseif followed>
          You are currently following this user.
          <a class="unfollow" href="/${profile_user.username?html}/unfollow">Unfollow user</a>.
        <#else>
          You are not yet following this user.
          <a class="follow" href="/${profile_user.username?html}/follow">Follow user</a>.
        </#if>
      </div>
    <#else>
      <div class="twitbox">
        <h3>What's on your mind ${user.username?html}?</h3>
        <form action="/add_message" method="post">
          <input type="hidden" name="csrf_token" value='${csrf_token?html}">
          <p><input type="text" name="text" size="60">
          <input type="submit" value="Share">
        </form>
      </div>
    </#if>
  </#if>

  <ul class="messages">
    <#list messages as message>
      <li>
        <img
          src="${GravatarUtil.getUrl(message.email, 48)}"
          alt="avatar for ${message.username?html}"
        >
        <p>
          <strong>
            <a href="/${message.username?html}">
              ${message.username?html}
            </a>
          </strong>
          ${message.text?html}
          <small>&mdash; ${DateUtil.format(message.pub_date)}</small>
        </p>
      </li>
    <#else>
      <li><em>There's no message so far.</em></li>
    </#list>
  </ul>
</@layout.layout>