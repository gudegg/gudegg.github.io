package gudetest.jaxb;


import com.sun.xml.internal.bind.marshaller.CharacterEscapeHandler;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author Gude
 * @Date 2016/4/8.
 */
public class JaxbTest {
    @Test
    public void ObjToXml() throws JAXBException {
        Person person = new Person("gude", 11, 1);
        person.setParent("继承");

        Teacher teacher = new Teacher("小丁", "数学");
        person.setTeacher(teacher);
        Work w1 = new Work("工作1");

        Work w2 = new Work("工作2");
        Set<Work> works = new HashSet<Work>();
        works.add(w1);
        works.add(w2);
        person.setWorks(works);


        JAXBContext jaxbContext = JAXBContext.newInstance(Person.class, PersonParent.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        //是否输出头 True不输出 <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        //格式化
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        //jaxbMarshaller.marshal(person, System.out);
        //jaxbMarshaller.setProperty("com.sun.xml.bind.xmlDeclaration", Boolean.FALSE);


        StringWriter writer = new StringWriter();

        jaxbMarshaller.marshal(person, writer);

        System.out.println(writer.toString());
    }

    @Test
    public void XmlToObj() throws JAXBException {
        File file = new File("E:\\idea_model\\gudetest\\src\\test\\java\\gudetest\\jaxb\\xml.xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(Person.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

        Person person = (Person) jaxbUnmarshaller.unmarshal(file);
        System.out.println(person);


    }

    @Test
    public void demo2() throws Exception {
        Customer customer = new Customer();
        customer.setBio("gude");

        JAXBContext jaxbContext = JAXBContext.newInstance(Customer.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        //        jaxbMarshaller.setProperty("com.sun.xml.internal.bind.characterEscapeHandler", new CharacterEscapeHandler() {
        //            @Override
        //            public void escape(char[] ac, int i, int j, boolean flag, Writer writer) throws IOException {
        //                // do not escape
        //                writer.write(ac, i, j);
        //            }
        //        });
        //是否输出头 True不输出 <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        //格式化
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        CDataContentHandler cDataContentHandler = new CDataContentHandler();
        jaxbMarshaller.marshal(customer, cDataContentHandler);
        Document document = cDataContentHandler.getCurrentElement().getDocument();
        XMLOutputter outputter = new XMLOutputter();
        StringWriter writer2 = new StringWriter();
        outputter.output(document, writer2);
        System.out.println(writer2.toString());

        StringWriter writer = new StringWriter();
        jaxbMarshaller.marshal(customer, writer);
        System.out.println(writer.toString());

    }

    @Test
    public void demo5() throws JAXBException {
        School school = new School();
        school.setName("guodong");
        JAXBContext jaxbContext = JAXBContext.newInstance(School.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        //格式化
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter writer = new StringWriter();
        jaxbMarshaller.marshal(school, writer);
        System.out.println(writer.toString());

    }

    @Test
    public void demo6() throws JAXBException {
        School school = new School();
        school.setName("guodong");
        JAXBContext jaxbContext = JAXBContext.newInstance(School.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        //格式化
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.setProperty(CharacterEscapeHandler.class.getName(), new CharacterEscapeHandler() {
            @Override
            public void escape(char[] ac, int i, int j, boolean flag, Writer writer) throws IOException {
                writer.write(ac, i, j);
            }
        });
        StringWriter writer = new StringWriter();
        jaxbMarshaller.marshal(school, writer);
        System.out.println(writer.toString());
    }

}
