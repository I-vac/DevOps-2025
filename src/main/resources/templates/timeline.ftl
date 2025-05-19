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
          <a class="unfollow" href="/${profile_user.username}/unfollow">Unfollow user</a>.
        <#else>
          You are not yet following this user.
          <a class="follow" href="/${profile_user.username}/follow">Follow user</a>.
        </#if>
      </div>
    <#else>
      <div class="twitbox">
        <h3>What's on your mind ${user.username}?</h3>
        <form action="/add_message" method="post">
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
          alt="avatar for ${message.username}"
        >
        <p>
          <strong>
            <a href="/${message.username}">
              ${message.username}
            </a>
          </strong>
          ${message.text}
          <small>&mdash; ${DateUtil.format(message.pub_date)}</small>
        </p>
      </li>
    <#else>
      <li><em>There's no message so far.</em></li>
    </#list>
  </ul>
</@layout.layout>