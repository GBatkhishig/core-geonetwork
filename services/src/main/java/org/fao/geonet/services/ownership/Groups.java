//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.services.ownership;

import static org.springframework.data.jpa.domain.Specifications.*;
import static org.fao.geonet.repository.specification.OperationAllowedSpecs.*;
import jeeves.interfaces.Service;
import jeeves.resources.dbms.Dbms;
import jeeves.server.ServiceConfig;
import jeeves.server.UserSession;
import jeeves.server.context.ServiceContext;
import org.fao.geonet.Util;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.domain.OperationAllowed;
import org.fao.geonet.kernel.AccessManager;
import org.fao.geonet.lib.Lib;
import org.fao.geonet.repository.OperationAllowedRepository;
import org.jdom.Element;
import org.springframework.data.jpa.domain.Specifications;

import java.util.List;
import java.util.Set;
 
//=============================================================================

public class Groups implements Service
{
	public void init(String appPath, ServiceConfig params) throws Exception {}

	//--------------------------------------------------------------------------
	//---
	//--- Service
	//---
	//--------------------------------------------------------------------------

	public Element exec(Element params, ServiceContext context) throws Exception
	{
		int userId = Util.getParamAsInt(params, "id");

		GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);
		UserSession   us = context.getUserSession();
		AccessManager am = gc.getBean(AccessManager.class);

		Dbms dbms = (Dbms) context.getResourceManager().open(Geonet.Res.MAIN_DB);

		Set<String> userGroups = am.getVisibleGroups(dbms, userId);
		Set<String> myGroups   = am.getUserGroups(dbms, us, null, false);

		//--- remove 'Intranet' and 'All' groups
		myGroups.remove("0");
		myGroups.remove("1");

		Element response = new Element("response");

		OperationAllowedRepository opAllowedRepo = context.getBean(OperationAllowedRepository.class);
		for (String groupId : userGroups)
		{
		    int iGroupId = Integer.valueOf(groupId);
            Specifications<OperationAllowed> spec = where(hasGroupId(iGroupId)).and(metadataHasOwnerId(userId));
		    long count = opAllowedRepo.count(spec);

			if (count > 0)
			{
				@SuppressWarnings("unchecked")
                List<Element> records = Lib.local.retrieveById(dbms, "Groups", groupId).getChildren();

				if (!records.isEmpty())
				{
					Element record  = records.get(0);
					record.detach();
					record.setName("group");

					response.addContent(record);
				}
			}
		}

		for (String groupId : myGroups)
		{
			@SuppressWarnings("unchecked")
            List<Element> records = Lib.local.retrieveById(dbms, "Groups", groupId).getChildren();

			if (!records.isEmpty())
			{
				Element record  = records.get(0);
				record.detach();
				record.setName("targetGroup");
				response.addContent(record);
				// List all group users or administrator
				String query = "SELECT id, surname, name FROM Users LEFT JOIN UserGroups ON (id = userId) "+
									" WHERE (groupId=? AND usergroups.profile != 'RegisteredUser') OR users.profile = 'Administrator'";

				Element editors = dbms.select(query, Integer.valueOf(groupId));

				for (Object o : editors.getChildren())
				{
					Element editor = (Element) o;
					editor = (Element) editor.clone();
					editor.removeChild("password");
					editor.setName("editor");

					record.addContent(editor);
				}
			}
		}

		return response;
	}
}

//=============================================================================
