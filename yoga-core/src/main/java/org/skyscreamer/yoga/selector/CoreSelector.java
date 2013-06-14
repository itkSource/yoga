package org.skyscreamer.yoga.selector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.skyscreamer.yoga.annotations.Core;
import org.skyscreamer.yoga.annotations.ExtraField;
import org.skyscreamer.yoga.configuration.DefaultEntityConfigurationRegistry;
import org.skyscreamer.yoga.configuration.EntityConfigurationRegistry;
import org.skyscreamer.yoga.configuration.YogaEntityConfiguration;
import org.skyscreamer.yoga.metadata.PropertyUtil;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CoreSelector extends MapSelector
{
    private EntityConfigurationRegistry _entityConfigurationRegistry = new DefaultEntityConfigurationRegistry();
    protected ConcurrentHashMap allCoreFields = new ConcurrentHashMap();

    public CoreSelector( EntityConfigurationRegistry entityConfigurationRegistry )
    {
        _entityConfigurationRegistry = entityConfigurationRegistry;
    }

    public CoreSelector()
    {
    }

    public void setEntityConfigurationRegistry( EntityConfigurationRegistry entityConfigurationRegistry )
    {
        _entityConfigurationRegistry = entityConfigurationRegistry;
    }

    @Override
    protected <T> Collection<Property<T>> getRegisteredFieldCollection( Class<T> instanceType )
    {
        return getProperties(instanceType, descriptors).values();
    }

    @Override
    public <T> Property<T> getProperty(Class<T> instanceType, String fieldName)
    {
        Map<String, Property<T>> properties = getProperties(instanceType, descriptors);
        if(properties != null)
        {
            return properties.get(fieldName);
        }
        return null;
    }

    private <T> Map<String, Property<T>> getProperties(Class<T> instanceType, ConcurrentHashMap map)
    {
        Map properties = (Map) map.get( instanceType );
        if (properties == null)
        {
            properties = createCoreFieldsCollection( instanceType );
            Map existingProperties = (Map) map.putIfAbsent( instanceType, properties );
            if( existingProperties != null )
            {
                properties = existingProperties;
            }
        }
        return properties;
    }

    private <T> Map<String, Property<T>> createCoreFieldsCollection( Class<T> instanceType )
    {
        Map<String, Property<T>> response = new HashMap<String, Property<T>>();
        List<PropertyDescriptor> readableProperties = PropertyUtil.getReadableProperties( instanceType );

        YogaEntityConfiguration<T> config = getConfig(instanceType);
        Collection<String> allowedCoreFields = config != null ? config.getCoreFields() : null;
        Collection<Property<T>> properties = config == null ? null : config.getProperties();

        if (allowedCoreFields == null)
        {
            for (PropertyDescriptor descriptor : readableProperties)
            {
                if (descriptor.getReadMethod().isAnnotationPresent( Core.class ))
                {
                    response.put( descriptor.getName(), createProperty( properties, instanceType, descriptor ) );
                }
            }
        }
        else
        {
            for (PropertyDescriptor descriptor : readableProperties)
            {
                if (allowedCoreFields.contains( descriptor.getName() ))
                {
                    response.put( descriptor.getName(), createProperty( properties, instanceType, descriptor ) );
                }
            }
        }

        return response;
    }

    protected <T> Property<T> createProperty(Collection<Property<T>> properties, Class<T> instanceType, PropertyDescriptor desc)
    {
        if( properties != null )
        {
            for ( Property<T> property : properties )
            {
                if(property.name().equals(desc.getName()))
                {
                    return property;
                }
            }
        }
        return new PojoProperty<T>( desc );
    }

    private <T> YogaEntityConfiguration<T> getConfig(Class<T> instanceType)
    {
        return _entityConfigurationRegistry != null 
                ? _entityConfigurationRegistry.getEntityConfiguration(instanceType) 
                : null;
    }

    @Override
    public <T> Collection<Property<T>> getAllPossibleFields( Class<T> instanceType )
    {
        return createAllFieldList(instanceType);
    }

    private <T> Collection<Property<T>> createAllFieldList(Class<T> instanceType)
    {
        Map<String, Property<T>> response = new TreeMap<String, Property<T>>();

        YogaEntityConfiguration<T> config = getConfig(instanceType);
        Collection<String> selectableFields = config == null ?  null : config.getSelectableFields();
        Collection<Property<T>> properties = config == null ? null : config.getProperties();

        // Get the getters
        for (PropertyDescriptor descriptor : PropertyUtil.getReadableProperties( instanceType ))
        {
            String name = descriptor.getName();
            if(selectableFields == null || selectableFields.contains(name))
            {
                response.put( name, createProperty( properties, instanceType, descriptor ) );
            }
        }

        // Add @ExtraField methods from the YogaEntityConfiguration, if one exists
        if (config != null)
        {
            for (Method method : config.getExtraFieldMethods())
            {
                String name = method.getAnnotation( ExtraField.class ).value();
                response.put(name, new ExtraFieldProperty<T>( name, config, method ) );
            }
        }

        return response.values();
    }
}
