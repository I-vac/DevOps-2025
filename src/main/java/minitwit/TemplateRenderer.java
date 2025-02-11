package minitwit;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public class TemplateRenderer {
    private static final Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_31);

    static {
        freemarkerConfig.setClassForTemplateLoading(TemplateRenderer.class, "/templates");
    }

    public static void configure() {
        freemarkerConfig.setClassForTemplateLoading(TemplateRenderer.class, "/templates");
    }

    public static String render(String templateName, Map<String, Object> model) {
        try (StringWriter writer = new StringWriter()) {
            Template template = freemarkerConfig.getTemplate(templateName + ".ftl");
            template.process(model, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new RuntimeException("Failed to render template: " + templateName, e);
        }
    }

    public static void addHelper(String name, TemplateMethodModelEx helper) {
        freemarkerConfig.setSharedVariable(name, helper);
    }
}