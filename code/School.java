package gudetest.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.Serializable;

/**
 * @Author Gude
 * @Date 2016/4/16.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
public class School implements Serializable{
    private static final long serialVersionUID = -6466566593287307981L;
    @XmlJavaTypeAdapter(Adapter.class)
    @XmlElement(name = "NAME")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
