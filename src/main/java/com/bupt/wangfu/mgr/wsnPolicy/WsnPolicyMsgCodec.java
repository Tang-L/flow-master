
package com.bupt.wangfu.mgr.wsnPolicy;

import com.bupt.wangfu.mgr.wsnPolicy.msgs.ComplexGroup;
import com.bupt.wangfu.mgr.wsnPolicy.msgs.TargetGroup;
import com.bupt.wangfu.mgr.wsnPolicy.msgs.TargetMsg;
import com.bupt.wangfu.mgr.wsnPolicy.msgs.WsnPolicyMsg;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

/**
 * encode后的格式如下:
 * <wsnPolicy>
 * <policyMsg targetTopic="host_insert_msg">
 * <arrayList name = "complexGroups">
 * <ComplexGroup name = "complexGroup1">
 * <ComplexGroup name = "complexGroup11">
 * <TargetGroup name = "targetGroup111">
 * ....
 * </TargetGroup>
 * </ComplexGroup>
 * <ComplexGroup name = "complexGroup12">
 * .....
 * </ComplexGroup>
 * </ComplexGroup>
 * </arrayList>
 * <arrayList name = "targetGroups">
 * <TargetGroup groupName="G1">
 * <TargetRep repIp="repAddr">
 * <TargetHost hostIp="clientAddr">
 * </TargetHost>
 * </TargetRep>
 * </TargetGroup>
 * <TargetGroup groupName="G2">
 * <TargetRep repIp="repAddr">
 * <TargetHost hostIp="clientAddr">
 * </TargetHost>
 * </TargetRep>
 * </TargetGroup>
 * </arrayList>
 * </policyMsg>
 * </wsnPolicy>
 */
public class WsnPolicyMsgCodec {

	private static final String MSGPACKAGE = "com.bupt.wangfu.mgr.wsnPolicy.msgs.";
	/**
	 * Holds the owner document of the codec.
	 */
	protected Document document;
	//是否完全编码。若为true，则编码粒度至host，若为false，则编码粒度至TargetGroup。
	//在编码PolicyMsg时，用完全编码，编码复合集群和单元集群信息时，用不完全编码.
	protected boolean codeAll = true;

	public WsnPolicyMsgCodec() {
		this(ShorenUtils.createDocument());
	}

	public WsnPolicyMsgCodec(Document document) {
		if (document == null) {
			document = ShorenUtils.createDocument();
		}

		this.document = document;
	}

	//将属性attr的值添加到指定Node上。
	public static void setAttribute(Node node, String attribute, Object value) {
		if (node.getNodeType() == Node.ELEMENT_NODE && attribute != null
				&& value != null) {
			((Element) node).setAttribute(attribute, String.valueOf(value));
		}
	}

	public boolean isCodeAll() {
		return codeAll;
	}

	public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	protected Node encodePolicyMsg(WsnPolicyMsg msg) {

		Node node = null;
		if (msg != null && msg.getTargetTopic() != null) {
			node = this.document.createElement("policyMsg");
			encodeFields(msg, node);
		}
		return node;
	}

	protected void encodeFields(Object obj, Node node) {
		Class type = obj.getClass();

		while (type != null) {
			Field[] fields = type.getDeclaredFields();

			for (Field f : fields) {
				//只有protected才解析
				if ((f.getModifiers() & Modifier.PROTECTED) == Modifier.PROTECTED) {
					String fieldname = f.getName();
					Object value = getFieldValue(obj, fieldname);
					encodeValue(fieldname, value, node);
				}
			}

			type = type.getSuperclass();
		}
	}

	//将指定的孩子（filedName是标签，值是value）加到node.
	@SuppressWarnings("rawtypes")
	protected void encodeValue(String filedName, Object value, Node node) {
		//string类型的值
		if (value != null && !(value instanceof List)) {
			setAttribute(node, filedName, value);
		} else if (value != null && !((List) value).isEmpty()) {

			Node children = this.document.createElement("array");
			setAttribute(children, "as", filedName);

			for (Object obj : ((List) value)) {
				if (obj instanceof TargetMsg) {
					//若编码粒度至TargetGroup,而obj不是ComplexGroup类，也不是TargetGroup类，则返回。
					if ((!isCodeAll()) && (!(obj instanceof TargetGroup
							|| obj instanceof ComplexGroup)))
						return;
					Class type = obj.getClass();
					String stype = String.valueOf(type);
					String label = stype.substring(stype.lastIndexOf(".") + 1);
					Node child = this.document.createElement(label);
					encodeFields(obj, child);
					children.appendChild(child);
				}
			}
			node.appendChild(children);
		}
	}

	protected Object getFieldValue(Object obj, String fieldname) {
		Object value = null;

		if (obj != null && fieldname != null) {
			Field field = getField(obj, fieldname);

			try {
				if (field != null) {
					value = field.get(obj);
				}
			} catch (IllegalAccessException e1) {
				try {
                    Method method = getAccessor(obj, field, true);
                    value = method.invoke(obj, (Object[]) null);
                } catch (Exception e2) {
                    // ignore
                }
			} catch (Exception e) {
				// ignore
			}
		}

		return value;
	}

	/**
	 * Returns the accessor (getter, setter) for the specified field.
	 */
	protected Method getAccessor(Object obj, Field field, boolean isGetter) {
		String name = field.getName();
		name = name.substring(0, 1).toUpperCase() + name.substring(1);

		if (!isGetter) {
			name = "set" + name;
		} else if (boolean.class.isAssignableFrom(field.getType())) {
			name = "is" + name;
		} else {
			name = "get" + name;
		}

		try {
			if (isGetter) {
				return getMethod(obj, name, null);
			} else {
				return getMethod(obj, name, new Class[]{field.getType()});
			}
		} catch (Exception e1) {
			// ignore
		}

		return null;
	}

	/**
	 * Returns the method with the specified signature.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	protected Method getMethod(Object obj, String methodname, Class[] params) {
		Class type = obj.getClass();

		while (type != null) {
			try {
				Method method = type.getDeclaredMethod(methodname, params);

				if (method != null) {
					return method;
				}
			} catch (Exception e) {
				// ignore
			}

			type = type.getSuperclass();
		}
		return null;
	}

	/**
	 * Returns the field with the specified name.
	 */
	@SuppressWarnings("rawtypes")
	protected Field getField(Object obj, String fieldname) {
		Class type = obj.getClass();

		while (type != null) {
			try {
				Field field = type.getDeclaredField(fieldname);

				if (field != null) {
					return field;
				}
			} catch (Exception e) {
				// ignore
				//	System.out.println("filedError!");
			}

			type = type.getSuperclass();
		}

		return null;
	}
	
	
	/*--------------------------- decode part -----------------------------------------*/
	/*
	 * 所有的孩子节点都放在list中.
	 * */

	protected void setFieldValue(Object obj, String fieldname, Object value) {
		Field field = null;

		try {
			field = getField(obj, fieldname);

			if (field.getType() == Boolean.class) {
				value = value.equals("1")
						|| String.valueOf(value).equalsIgnoreCase("true");
			}

			field.set(obj, value);
		} catch (IllegalAccessException e1) {
			try {

                Method method = getAccessor(obj, field, false);
                Class type = method.getParameterTypes()[0];
                value = convertValueFromXml(type, value);

                // Converts collection to a typed array before setting
                if (type.isArray() && value instanceof Collection) {
                    Collection coll = (Collection) value;
                    value = coll.toArray((Object[]) Array.newInstance(type
                            .getComponentType(), coll.size()));
                }

                //value是method参数，是不是该知道参数的类型先？然后用value来生成参数
                if (value != null)
                    method.invoke(obj, value);
            } catch (Exception e2) {
                System.err.println("setFieldValue: " + e2 + " on "
                        + obj.getClass().getSimpleName() + "." + fieldname
                        + " (" + field.getType().getSimpleName() + ") = "
                        + value + " (" + value.getClass().getSimpleName()
                        + ")");
            }
		} catch (Exception e) {
			// ignore
		}
	}

	/**
	 * Converts XML attribute values to object of the given type.
	 */
	protected Object convertValueFromXml(Class type, Object value) {
		if (value instanceof String && type.isPrimitive()) {
			String tmp = (String) value;

			if (type.equals(boolean.class)) {
				if (tmp.equals("1") || tmp.equals("0")) {
					tmp = (tmp.equals("1")) ? "true" : "false";
				}

				value = Boolean.valueOf(tmp);
			} else if (type.equals(char.class)) {
				value = tmp.charAt(0);
			} else if (type.equals(byte.class)) {
				value = new Byte(tmp);
			} else if (type.equals(short.class)) {
				value = new Short(tmp);
			} else if (type.equals(int.class)) {
				value = new Integer(tmp);
			} else if (type.equals(long.class)) {
				value = new Long(tmp);
			} else if (type.equals(float.class)) {
				value = new Float(tmp);
			} else if (type.equals(double.class)) {
				value = new Double(tmp);
			}
		}
		if (value instanceof String && (value.toString().equals("") ||
				value.toString().equals("null")) && !type.isPrimitive())
			return null;

		return value;
	}


	protected void decodePolicyMsg(Node node, Object obj) {
		decodeAttributes(node, obj);
		decodeChildren(node, obj);
	}

	//遍历属性，再调用setFieldValue
	protected void decodeAttributes(Node node, Object obj) {
		if (node.hasAttributes()) {
			NamedNodeMap attrs = node.getAttributes();
			for (int i = 0; i < attrs.getLength(); i++) {
				Node attr = attrs.item(i);
				String fieldname = attr.getNodeName();
				String value = attr.getNodeValue();
				setFieldValue(obj, fieldname, value);
			}
		}

	}

	protected void decodeChildren(Node node, Object obj) {
		if (node.hasChildNodes()) {
			Node child = node.getFirstChild();
			while (child != null) {

				if (child.getNodeType() == Node.TEXT_NODE && !child.getTextContent().startsWith("\n")) //protected的属性
				{
					decodeAttributes(child, obj);
				}

				if (child.getNodeType() == Node.ELEMENT_NODE) {
					//若obj是wsnPolicy的相关类
					if (!isPrimitiveValue(obj, child.getNodeName())) {
						Object childObj = null;
						try {
							childObj = Class.forName(MSGPACKAGE + child.getNodeName()).newInstance();
							setFieldValue(obj, childObj);
						} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
							e.printStackTrace();
						}
						decodeAttributes(child, childObj);
						decodeChildren(child, childObj);
						if (obj instanceof Collection) {
							((Collection) obj).add(childObj);
						}
					} else if (child.getNodeName().equals("array")) {
						NamedNodeMap attrs = child.getAttributes();
						for (int i = 0; i < attrs.getLength(); i++) {
							Node attr = attrs.item(i);
							String attrname = attr.getNodeName();
							if (attrname.equals("as")) {
								String fieldname = attr.getNodeValue();
								Field field = getField(obj, fieldname);

								Object value = null;

								Class type = field.getType();
								Class type1 = field.getDeclaringClass();
								String type2 = field.toGenericString();


								try {
									value = field.get(obj);
								} catch (IllegalAccessException e1) {
									try {
                                        Method method = getAccessor(obj, field, true);
                                        value = method.invoke(obj, (Object[]) null);
                                    } catch (Exception ignored) {
                                    }
								} catch (Exception ignored) {
								}
								setFieldValue(obj, fieldname, value);
								decodeChildren(child, value);
							}
						}
					}
				}
				child = child.getNextSibling();
			}
		}
	}

	//suppose change the name to setFieldValues
	public void setFieldValue(Object obj, Object value) {
		Class type = obj.getClass();
		if (type != null) {
			Field[] fields = type.getDeclaredFields();
			for (Field field : fields) {
				try {
					Object o = getFieldValue(obj, field.getName());
					if (field.getType().getName().equals(value.getClass().getName())
							&& o == null) {
						this.setFieldValue(obj, field.getName(), value);
						return;
					}
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected boolean isPrimitiveValue(Object obj, String value) {
		boolean is = true;
		try {
			Class.forName(MSGPACKAGE + value).newInstance();
			is = false;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			return is;
		}
		return is;
	}
}
