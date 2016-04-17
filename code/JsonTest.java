package gudetest.Json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.Feature;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @Author Gude
 * @Date 2016/4/17.
 */
public class JsonTest {
    @Test
    public void objToJson() {
        Person person = new Person("gude", 21);
        String res = JSON.toJSONString(person);
        System.out.println(res);

    }

    @Test
    public void JsonToObj() {
        String json = "{\"age\":21,\"name\":\"gude\"}";
        Person person = JSON.parseObject(json, new TypeReference<Person>() {});
        System.out.println(person.toString());

        Person person2 = JSON.parseObject(json, Person.class);
        System.out.println(person2);
    }

    @Test
    public void objToJson2() {
        Person person = new Person("gude", 21);
        Person person1 = new Person("gude2", 22);
        List list = new ArrayList();
        list.add(person);
        list.add(person1);

        String res = JSON.toJSONString(list);
        System.out.println(res);

    }

    @Test
    public void JsonToObj2() {
        String json = "[{\"age\":21,\"name\":\"gude\"},{\"age\":22,\"name\":\"gude2\"}]";
        List<Person> person = JSON.parseObject(json, new TypeReference<List<Person>>() {
        });
        System.out.println(person.toString());

        List<Person> person2 = JSON.parseArray(json, Person.class);
        System.out.println(person2);
    }

    @Test
    public void JsonToObj3() {
        String json = "[{\"age\":21,\"name\":\"gude\",\"pass\":\"gudegg\"},{\"age\":22,\"name\":\"gude2\"}]";
        List<Map<String,Object>> person = JSON.parseObject(json, new TypeReference<List<Map<String,Object>>>() {
        });
        System.out.println(person.toString());
        List<Person> person3 = JSON.parseObject(json, new TypeReference<List<Person>>() {
        });
        System.out.println(person3.toString());
        List<Person> person2 = JSON.parseArray(json, Person.class);
        System.out.println(person2);
    }
}
