# GitFileProvider

This is a JSPWiki [http://jspwiki.apache.org/] PageProvider that uses Git for its page and attachment history.

It is based on JGit [https://eclipse.org/jgit/].

It works by committing after every page save.

Right now, it is not very performant, as JSPWiki starts counting versions with the oldest, so you have to got back Gits commit history to find out how many versions there are.

