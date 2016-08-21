/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

package de.jwi.jspwiki.git;

import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.auth.NoSuchPrincipalException;
import org.apache.wiki.auth.user.UserDatabase;
import org.apache.wiki.auth.user.UserProfile;

public class GitUtil
{
	private WikiEngine engine;
	
	public GitUtil(WikiEngine engine)
	{
		this.engine = engine;
	}

	protected GitVersion getGitVersion(WikiPage page)
	{
		GitVersion gitVersion = new GitVersion();

		gitVersion.author = page.getAuthor();
		gitVersion.changenote = (String) page.getAttribute(WikiPage.CHANGENOTE);

		gitVersion.email = null;

		UserDatabase userDatabase = engine.getUserManager().getUserDatabase();

		try
		{
			UserProfile userProfile = userDatabase.find(gitVersion.author);
			gitVersion.email = userProfile.getEmail();

		} catch (NoSuchPrincipalException e1)
		{
			gitVersion.author = "unknown";
			gitVersion.email = "unknown@unknown";
		}

		return gitVersion;
	}

}
