/*
 * (c) 2012 Martin van Zuilekom (http://martin.cubeactive.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.lechucksoftware.proxy.proxysettings.excluded;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.format.DateFormat;
import android.util.Log;

import com.lechucksoftware.proxy.proxysettings.R;
import com.lechucksoftware.proxy.proxysettings.utils.UIUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class to show a change log dialog
 */
public class ChangeLogDialog
{
    private static final String TAG = ChangeLogDialog.class.getSimpleName();

    private final Context mContext;
    private String mStyle = "";

    protected DialogInterface.OnDismissListener mOnDismissListener;

    public ChangeLogDialog(final Context context)
    {
        mContext = context;
    }

    protected Context getContext()
    {
        return mContext;
    }

    //Get the current app version
    private String getAppVersion()
    {
        String versionName = "";
        try
        {
            final PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            versionName = packageInfo.versionName;
        }
        catch (NameNotFoundException e)
        {
            Log.e(TAG, e.getMessage(), e);
        }
        return versionName;
    }

    //Parse a date string from the xml and format it using the local date format
    @SuppressLint("SimpleDateFormat")
    private String parseDate(final String dateString)
    {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        try
        {
            final Date parsedDate = dateFormat.parse(dateString);
            return DateFormat.getDateFormat(getContext()).format(parsedDate);
        }
        catch (ParseException ignored)
        {
            //If there is a problem parsing the date just return the original string
            return dateString;
        }
    }

    //Parse a the release tag and appends it to the changelog builder
    private void parseReleaseTag(final StringBuilder changelogBuilder, final XmlPullParser resourceParser) throws XmlPullParserException, IOException
    {
        changelogBuilder.append("<h1>Release: ").append(resourceParser.getAttributeValue(null, "version")).append("</h1>");

        //Add date if available
        if (resourceParser.getAttributeValue(null, "date") != null)
        {
            changelogBuilder.append("<span class='date'>").append(parseDate(resourceParser.getAttributeValue(null, "date"))).append("</span>");
        }

        //Add summary if available
        if (resourceParser.getAttributeValue(null, "summary") != null)
        {
            changelogBuilder.append("<span class='summary'>").append(resourceParser.getAttributeValue(null, "summary")).append("</span>");
        }

        changelogBuilder.append("<ul>");

        //Parse child nodes
        int eventType = resourceParser.getEventType();
        while ((eventType != XmlPullParser.END_TAG) || (resourceParser.getName().equals("change")))
        {
            if ((eventType == XmlPullParser.START_TAG) && (resourceParser.getName().equals("change")))
            {
                eventType = resourceParser.next();
                changelogBuilder.append("<li>");
                changelogBuilder.append(resourceParser.getText());
                changelogBuilder.append("</li>");
            }
            eventType = resourceParser.next();
        }
        changelogBuilder.append("</ul>");
    }

    //CSS style for the html
    private String getHead()
    {
        return String.format("<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge,chrome=1\">\n" +
                "    <title>About Proxy Settings</title>\n" +
                "    <meta name=\"description\" content=\"\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "    <link rel=\"stylesheet\" href=\"../css/bootstrap.min.css\">\n" +
                "    <link rel=\"stylesheet\" href=\"../css/bootstrap-theme.min.css\">\n" +
                "    <link rel=\"stylesheet\" href=\"../css/main.css\">\n" +
                "    <script src=\"../js/vendor/modernizr-2.6.2-respond-1.1.0.min.js\"></script>\n" +
                "</head>");
    }

    public void setStyle(final String style)
    {
        mStyle = style;
    }

    public ChangeLogDialog setOnDismissListener(final DialogInterface.OnDismissListener onDismissListener)
    {
        mOnDismissListener = onDismissListener;
        return this;
    }

    //Get the changelog in html code, this will be shown in the dialog's webview
    private String getHTMLChangelog(final int resourceId, final Resources resources, final int version)
    {
        boolean releaseFound = false;
        final StringBuilder changelogBuilder = new StringBuilder();
        changelogBuilder.append("<html>").append(getHead()).append("<body>");
        final XmlResourceParser xml = resources.getXml(resourceId);
        try
        {
            int eventType = xml.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT)
            {
                if ((eventType == XmlPullParser.START_TAG) && (xml.getName().equals("release")))
                {
                    //Check if the version matches the release tag.
                    //When version is 0 every release tag is parsed.
                    final int versioncode = Integer.parseInt(xml.getAttributeValue(null, "versioncode"));
                    if ((version == 0) || (versioncode == version))
                    {
                        parseReleaseTag(changelogBuilder, xml);
                        releaseFound = true; //At lease one release tag has been parsed.
                    }
                }
                eventType = xml.next();
            }
        }
        catch (XmlPullParserException e)
        {
            Log.e(TAG, e.getMessage(), e);
            return "";
        }
        catch (IOException e)
        {
            Log.e(TAG, e.getMessage(), e);
            return "";
        }
        finally
        {
            xml.close();
        }
        changelogBuilder.append("</body></html>");

        //Check if there was a release tag parsed, if not return an empty string.
        if (releaseFound)
        {
            return changelogBuilder.toString();
        }
        else
        {
            return "";
        }
    }

    //Returns change log in HTML format 
    public String getHTML()
    {
        //TODO: Remove duplicate code with the method show()
        //Get resources
        final String packageName = mContext.getPackageName();
        final Resources resources;
        try
        {
            resources = mContext.getPackageManager().getResourcesForApplication(packageName);
        }
        catch (NameNotFoundException ignored)
        {
            return "";
        }

        //Create HTML change log
        return getHTMLChangelog(R.xml.changelog, resources, 0);
    }

    //Call to show the change log dialog
    public void show()
    {
        show(0);
    }

    protected void show(final int version)
    {
        //Get resources
        final String packageName = mContext.getPackageName();
        final Resources resources;
        try
        {
            resources = mContext.getPackageManager().getResourcesForApplication(packageName);
        }
        catch (NameNotFoundException ignored)
        {
            return;
        }

        //Get dialog title	        	
        String title = resources.getString(R.string.title_changelog);
        title = String.format("%s v%s", title, getAppVersion());

        //Create html change log
        final String htmlChangelog = getHTMLChangelog(R.xml.changelog, resources, version);

        //Get button strings
        final String closeString = resources.getString(R.string.close);

        //Check for empty change log
        if (htmlChangelog.length() == 0)
        {
            //It seems like there is nothing to show, just bail out.
            return;
        }

        UIUtils.showHTMLAlertDialog(mContext, title, htmlChangelog, closeString, mOnDismissListener);
    }
}

