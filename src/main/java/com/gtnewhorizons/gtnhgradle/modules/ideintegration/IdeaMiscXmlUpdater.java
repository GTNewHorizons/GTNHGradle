package com.gtnewhorizons.gtnhgradle.modules.ideintegration;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class IdeaMiscXmlUpdater {

    /** The default misc.xml for IntelliJ */
    private static final @NotNull String MISC_XML_DEFAULT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="EntryPointsManager">
            <list size="4">
              <item index="0" class="java.lang.String" itemvalue="cpw.mods.fml.common.Mod.EventHandler" />
              <item index="1" class="java.lang.String" itemvalue="cpw.mods.fml.common.eventhandler.SubscribeEvent" />
              <item index="2" class="java.lang.String" itemvalue="net.minecraftforge.fml.common.eventhandler.SubscribeEvent" />
              <item index="3" class="java.lang.String" itemvalue="org.spongepowered.asm.mixin.Mixin" />
            </list>
          </component>
          <component name="ExternalStorageConfigurationManager" enabled="true" />
          <component name="ProjectRootManager" version="2" languageLevel="JDK_17" project-jdk-name="11" project-jdk-type="JavaSDK">
            <output url="file://$PROJECT_DIR$/build/ideaBuild" />
          </component>
        </project>
        """;

    public static void mergeOrCreate(Path target) throws Exception {
        SAXBuilder builder = new SAXBuilder();
        Document refDoc = builder.build(new StringReader(MISC_XML_DEFAULT));
        Document targetDoc;

        if (Files.exists(target)) {
            targetDoc = builder.build(Files.newInputStream(target));
        } else {
            targetDoc = refDoc.clone();
            writeDocument(targetDoc, target);
            return;
        }

        Element refRoot = refDoc.getRootElement();
        Element tgtRoot = targetDoc.getRootElement();

        for (Element refComp : refRoot.getChildren("component")) {
            String name = refComp.getAttributeValue("name");

            if ("EntryPointsManager".equals(name)) {
                Element tgtComp = null;
                for (Element c : tgtRoot.getChildren("component")) {
                    if ("EntryPointsManager".equals(c.getAttributeValue("name"))) {
                        tgtComp = c;
                        break;
                    }
                }

                if (tgtComp == null) {
                    tgtRoot.addContent(refComp.clone());
                } else {
                    mergeList(refComp.getChild("list"), tgtComp.getChild("list"));
                }
            } else {
                tgtRoot.getChildren().removeIf(c -> name.equals(c.getAttributeValue("name")));
                tgtRoot.addContent(refComp.clone());
            }
        }

        writeDocument(targetDoc, target);
    }

    private static void mergeList(Element modelList, Element targetList) {
        if (targetList == null) {
            targetList = new Element("list");
        }

        for (Element modelItem : modelList.getChildren("item")) {
            String cls = modelItem.getAttributeValue("class");
            boolean exists = false;
            for (Element tgtItem : targetList.getChildren("item")) {
                if (cls.equals(tgtItem.getAttributeValue("class"))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                targetList.addContent(modelItem.clone());
            }
        }

        int idx = 0;
        for (Element it : targetList.getChildren("item")) {
            it.setAttribute("index", String.valueOf(idx++));
        }
        targetList.setAttribute("size", String.valueOf(targetList.getChildren("item").size()));
    }

    private static void writeDocument(Document doc, Path target) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            XMLOutputter out = new XMLOutputter(Format.getPrettyFormat().setIndent("  ").setLineSeparator("\n"));
            out.output(doc, writer);
        }
    }
}
