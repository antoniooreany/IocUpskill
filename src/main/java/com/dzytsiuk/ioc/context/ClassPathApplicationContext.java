package com.dzytsiuk.ioc.context;


import com.dzytsiuk.ioc.context.cast.JavaNumberTypeCast;
import com.dzytsiuk.ioc.entity.Bean;
import com.dzytsiuk.ioc.entity.BeanDefinition;
import com.dzytsiuk.ioc.io.BeanDefinitionReader;
import com.dzytsiuk.ioc.io.XMLBeanDefinitionReader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassPathApplicationContext implements ApplicationContext {

    private static final String SETTER_PREFIX = "set";
    private static final int SETTER_PARAMETER_INDEX = 0;

    private Map<String, Bean> beans;
    private BeanDefinitionReader beanDefinitionReader;

    public ClassPathApplicationContext() {
    }

    public ClassPathApplicationContext(String... path) {
        setBeanDefinitionReader(new XMLBeanDefinitionReader(path));
        start();
    }

    public void start() {
        beans = new HashMap<>();
        List<BeanDefinition> beanDefinitions = beanDefinitionReader.getBeanDefinitions();
        instantiateBeans(beanDefinitions);
        injectValueDependencies(beanDefinitions);
        injectRefDependencies(beanDefinitions);
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Cannot instantiate class '" + clazz + "'", e);
        }
    }

    @Override
    public <T> T getBean(String id, Class<T> clazz) {
        T beanById = (T) beans.get(id);
        if (beanById.getClass() != clazz) {
            throw new ClassCastException();
        }
        return beanById;
    }

    @Override
    public <T> T getBean(String id) {
        return (T) beans.get(id);
    }

    @Override
    public void setBeanDefinitionReader(BeanDefinitionReader beanDefinitionReader) {
        this.beanDefinitionReader = beanDefinitionReader;
    }

    private void instantiateBeans(List<BeanDefinition> beanDefinitions) {
        for (BeanDefinition beanDefinition : beanDefinitions) {
            Object value;
            try {
                value = Class.forName(beanDefinition.getBeanClassName()).newInstance();
            } catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
                throw new RuntimeException(e); //TODO
            }
            String id = beanDefinition.getId();
            Bean bean = new Bean();
            bean.setId(id);
            bean.setValue(value);
            beans.put(id, bean);
        }
    }

    private void injectValueDependencies(List<BeanDefinition> beanDefinitions) {
        for (BeanDefinition beanDefinition : beanDefinitions) {
            Map<String, String> dependencies = beanDefinition.getDependencies();
            injectDependencies(dependencies, beanDefinition);
        }
    }

    private void injectRefDependencies(List<BeanDefinition> beanDefinitions) {
        for (BeanDefinition beanDefinition : beanDefinitions) {
            Map<String, String> dependencies = beanDefinition.getRefDependencies();
            injectDependencies(dependencies, beanDefinition);
        }
    }

    private void injectDependencies(Map<String, String> dependencies, BeanDefinition beanDefinition) {
        for (String propertyName : dependencies.keySet()) {
            String setterName = getSetterName(propertyName);
            String id = beanDefinition.getId();
            Bean bean = beans.get(id);
            Object beanValue = bean.getValue();
            for (Method method : beanValue.getClass().getMethods()) {
                if (method.getName().equals(setterName)) {
                    Parameter parameter = method.getParameters()[SETTER_PARAMETER_INDEX];
//                    Class<? extends Parameter> fieldClass = parameter.getClass();
                    Class<?> parameterType = parameter.getType();
                    String propertyValue = dependencies.get(propertyName);
                    try {
                        if (parameterType.isPrimitive()) {
                            Integer primitivePropertyValue =
                                    (Integer) JavaNumberTypeCast.castPrimitive(propertyValue, parameterType); //TODO 'Integer' is hardcoded.
                            method.invoke(beanValue, primitivePropertyValue);
                        } else {
                            method.invoke(beanValue, propertyValue); //TODO java.lang.IllegalArgumentException: argument type mismatch: String or int as a parameter. How to know where is needed to cast, and where is not?
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private String getSetterName(String propertyName) {
        return SETTER_PREFIX + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
    }
}
