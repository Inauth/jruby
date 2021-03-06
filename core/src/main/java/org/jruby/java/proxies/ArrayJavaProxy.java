package org.jruby.java.proxies;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.jruby.*;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.java.util.ArrayUtils;
import org.jruby.javasupport.Java;
import org.jruby.javasupport.JavaArray;
import org.jruby.javasupport.JavaClass;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

public final class ArrayJavaProxy extends JavaProxy {

    private final JavaUtil.JavaConverter converter;

    public ArrayJavaProxy(Ruby runtime, RubyClass klazz, Object array) {
        this(runtime, klazz, array, JavaUtil.getJavaConverter(array.getClass().getComponentType()));
    }

    public ArrayJavaProxy(Ruby runtime, RubyClass klazz, Object array, JavaUtil.JavaConverter converter) {
        super(runtime, klazz, array);
        this.converter = converter;
    }

    public static RubyClass createArrayJavaProxy(ThreadContext context) {
        Ruby runtime = context.runtime;

        RubyClass arrayJavaProxy = runtime.defineClass("ArrayJavaProxy",
                runtime.getJavaSupport().getJavaProxyClass(),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);

        RubyClass singleton = arrayJavaProxy.getSingletonClass();
        singleton.addMethod("new", new ArrayNewMethod(singleton, Visibility.PUBLIC));

        arrayJavaProxy.defineAnnotatedMethods(ArrayJavaProxy.class);
        arrayJavaProxy.includeModule(runtime.getEnumerable());

        return arrayJavaProxy;
    }

    static ArrayJavaProxy newArray(final Ruby runtime, final Class<?> elementType, final int... dimensions) {
        final Object array;
        try {
            array = Array.newInstance(elementType, dimensions);
        }
        catch (IllegalArgumentException e) {
            throw runtime.newArgumentError("can not create " + dimensions.length + " dimensional array");
        }
        return new ArrayJavaProxy(runtime, Java.getProxyClassForObject(runtime, array), array);
    }

    protected JavaArray asJavaObject(final Object array) {
        return new JavaArray(getRuntime(), array);
    }

    public final JavaArray getJavaArray() {
        return (JavaArray) dataGetStruct();
    }

    public Object get(final int index) {
        return Array.get(getObject(), index);
    }

    public void set(final int index, final Object value) {
        Array.set(getObject(), index, value);
    }

    public IRubyObject setValue(final Ruby runtime, final int index, final IRubyObject value) {
        return ArrayUtils.asetDirect(runtime, getObject(), converter, index, value);
    }

    public final int length() { return Array.getLength( getObject() ); }

    @JRubyMethod(name = {"length", "size"})
    public RubyFixnum length(ThreadContext context) {
        return context.runtime.newFixnum( length() );
    }

    @JRubyMethod(name = "empty?")
    public RubyBoolean empty_p(ThreadContext context) {
        return context.runtime.newBoolean( length() == 0 );
    }

    @JRubyMethod(name = "[]")
    public final IRubyObject op_aref(ThreadContext context, IRubyObject arg) {
        if ( arg instanceof RubyRange ) return arrayRange(context, (RubyRange) arg);
        final int i = convertArrayIndex(arg);
        return ArrayUtils.arefDirect(context.runtime, getObject(), converter, i);
    }

    @JRubyMethod(name = "[]", required = 1, rest = true)
    public final IRubyObject op_aref(ThreadContext context, IRubyObject[] args) {
        if ( args.length == 1 ) return op_aref(context, args[0]);
        return getRange(context, args);
    }

    @JRubyMethod(name = "[]=")
    public final IRubyObject op_aset(ThreadContext context, IRubyObject index, IRubyObject value) {
        return setValue(context.runtime, convertArrayIndex(index), value);
    }

    @JRubyMethod(name = { "include?", "member?" }) // Enumerable override
    public IRubyObject include_p(ThreadContext context, IRubyObject obj) {
        final Object array = getObject();

        final Class<?> componentClass = array.getClass().getComponentType();

        if ( componentClass.isPrimitive() ) {
            switch (componentClass.getName().charAt(0)) {
                case 'b':
                    if (componentClass == byte.class) return context.runtime.newBoolean( includes(context, (byte[]) array, obj) );
                    else /* if (componentClass == boolean.class) */ return context.runtime.newBoolean( includes(context, (boolean[]) array, obj) );
                    // break;
                case 's':
                    /* if (componentClass == short.class) */ return context.runtime.newBoolean( includes(context, (short[]) array, obj) );
                    // break;
                case 'c':
                    /* if (componentClass == char.class) */ return context.runtime.newBoolean( includes(context, (char[]) array, obj) );
                    // break;
                case 'i':
                    /* if (componentClass == int.class) */ return context.runtime.newBoolean( includes(context, (int[]) array, obj) );
                    // break;
                case 'l':
                    /* if (componentClass == long.class) */ return context.runtime.newBoolean( includes(context, (long[]) array, obj) );
                    // break;
                case 'f':
                    /* if (componentClass == float.class) */ return context.runtime.newBoolean( includes(context, (float[]) array, obj) );
                    // break;
                case 'd':
                    /* if (componentClass == double.class) */ return context.runtime.newBoolean( includes(context, (double[]) array, obj) );
                    // break;
            }
        }
        return context.runtime.newBoolean( includes(context, (Object[]) array, obj) );
    }

    private boolean includes(final ThreadContext context, final Object[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = JavaUtil.convertJavaArrayElementToRuby(runtime, converter, array, i);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final byte[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFixnum ) {
            final long objVal = ((RubyInteger) obj).getLongValue();
            if ( objVal < Byte.MIN_VALUE || objVal > Byte.MAX_VALUE ) return false;

            for ( int i = 0; i < len; i++ ) {
                if ( (byte) objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFixnum.newFixnum(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final short[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFixnum ) {
            final long objVal = ((RubyInteger) obj).getLongValue();
            if ( objVal < Short.MIN_VALUE || objVal > Short.MAX_VALUE ) return false;

            for ( int i = 0; i < len; i++ ) {
                if ( (short) objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFixnum.newFixnum(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final int[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFixnum ) {
            final long objVal = ((RubyInteger) obj).getLongValue();
            if ( objVal < Integer.MIN_VALUE || objVal > Integer.MAX_VALUE ) return false;

            for ( int i = 0; i < len; i++ ) {
                if ( (int) objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFixnum.newFixnum(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final long[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFixnum ) {
            final long objVal = ((RubyInteger) obj).getLongValue();

            for ( int i = 0; i < len; i++ ) {
                if ( objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFixnum.newFixnum(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final char[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFixnum ) {
            final long objVal = ((RubyInteger) obj).getLongValue();
            if ( objVal < Character.MIN_VALUE || objVal > Character.MAX_VALUE ) return false;

            for ( int i = 0; i < len; i++ ) {
                if ( (int) objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFixnum.newFixnum(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final boolean[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyBoolean ) {
            final boolean objVal = ((RubyBoolean) obj).isTrue();

            for ( int i = 0; i < len; i++ ) {
                if ( objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyBoolean.newBoolean(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final float[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFloat ) {
            final double objVal = ((RubyFloat) obj).getDoubleValue();

            for ( int i = 0; i < len; i++ ) {
                if ( (float) objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFloat.newFloat(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    private boolean includes(final ThreadContext context, final double[] array, final IRubyObject obj) {
        final int len = array.length;
        if ( len == 0 ) return false;
        final Ruby runtime = context.runtime;
        if ( obj instanceof RubyFloat ) {
            final double objVal = ((RubyFloat) obj).getDoubleValue();

            for ( int i = 0; i < len; i++ ) {
                if ( objVal == array[i] ) return true;
            }
            return false;
        }
        for ( int i = 0; i < len; i++ ) {
            IRubyObject value = RubyFloat.newFloat(runtime, array[i]);
            if ( equalInternal(context, value, obj) ) return true;
        }
        return false;
    }

    @JRubyMethod(name = "first") // Enumerable override
    public IRubyObject first(ThreadContext context) {
        final Object array = getObject();
        // Enumerable does not raise when length == 0 (compatibility)
        if ( Array.getLength(array) == 0 ) return context.nil;
        return JavaUtil.convertJavaArrayElementToRuby(context.runtime, converter, array, 0);
    }

    @JRubyMethod(name = "first") // Enumerable override
    public IRubyObject first(ThreadContext context, IRubyObject count) {
        final Object array = getObject();
        int len = count.convertToInteger().getIntValue();
        int size = Array.getLength(array); if ( len > size ) len = size;

        final Ruby runtime = context.runtime;
        if ( len == 0 ) return RubyArray.newEmptyArray(runtime);
        final IRubyObject[] ary = new IRubyObject[len];
        for ( int i = 0; i < len; i++ ) {
            ary[i] = JavaUtil.convertJavaArrayElementToRuby(runtime, converter, array, i);
        }
        return RubyArray.newArrayNoCopy(runtime, ary);
    }

    @JRubyMethod(name = "last") // ~ Array#last
    public IRubyObject last(ThreadContext context) {
        final Object array = getObject();
        final int len = Array.getLength(array);
        if ( len == 0 ) return context.nil;
        return JavaUtil.convertJavaArrayElementToRuby(context.runtime, converter, array, len - 1);
    }

    @JRubyMethod(name = "last") // ~ Array#last
    public IRubyObject last(ThreadContext context, IRubyObject count) {
        final Object array = getObject();

        int len = RubyFixnum.fix2int(count);
        int size = Array.getLength(array);
        int start = size - len; if ( start < 0 ) start = 0;
        int end = start + len; if ( end > size ) end = size;
        len = end - start;

        final Ruby runtime = context.runtime;
        if ( len == 0 ) return RubyArray.newEmptyArray(runtime);
        final IRubyObject[] ary = new IRubyObject[len];
        for ( int i = 0; i < len; i++ ) {
            ary[i] = JavaUtil.convertJavaArrayElementToRuby(runtime, converter, array, i + start);
        }
        return RubyArray.newArrayNoCopy(runtime, ary);
    }

    @JRubyMethod(name = "count") // @override Enumerable#count
    public IRubyObject count(final ThreadContext context, final Block block) {
        final Ruby runtime = context.runtime;
        if ( block.isGiven() ) {
            final Object array = getObject(); int count = 0;
            for ( int i = 0; i < Array.getLength(array); i++ ) {
                IRubyObject next = JavaUtil.convertJavaArrayElementToRuby(runtime, converter, array, i);
                if ( block.yield( context, next ).isTrue() ) count++;
            }
            return RubyFixnum.newFixnum(runtime, count);
        }
        return RubyFixnum.newFixnum(runtime, length());
    }

    @JRubyMethod(name = "count") // @override Enumerable#count
    public IRubyObject count(final ThreadContext context, final IRubyObject obj, final Block unused) {
        // unused block due DescriptorInfo not (yet) supporting if a method receives block and an override doesn't
        final Ruby runtime = context.runtime;
        final Object array = getObject(); int count = 0;
        for ( int i = 0; i < Array.getLength(array); i++ ) {
            // NOTE: could be former improved by special case handling primitive arrays and == ...
            IRubyObject next = JavaUtil.convertJavaArrayElementToRuby(runtime, converter, array, i);
            if ( RubyObject.equalInternal(context, next, obj) ) count++;
        }
        return RubyFixnum.newFixnum(runtime, count);
    }

    @JRubyMethod(name = "dig", required = 1, rest = true)
    public final IRubyObject dig(ThreadContext context, IRubyObject[] args) {
        return dig(context, args, 0);
    }

    final IRubyObject dig(ThreadContext context, IRubyObject[] args, int idx) {
        final IRubyObject val = at(context, args[idx++]);
        return idx == args.length ? val : RubyObject.dig(context, val, args, idx);
    }

    private static int convertArrayIndex(final IRubyObject index) {
        if ( index instanceof JavaProxy ) {
            return (Integer) index.toJava(Integer.class);
        }
        return RubyNumeric.num2int(index);
    }

    @JRubyMethod
    public IRubyObject at(ThreadContext context, IRubyObject index) {
        return at(context, convertArrayIndex(index));
    }

    private final IRubyObject at(ThreadContext context, int i) {
        final Ruby runtime = context.runtime;
        final Object array = getObject();
        final int length = Array.getLength(array);

        if ( i < 0 ) i = i + length;

        if ( i >= 0 && i < length ) {
            return ArrayUtils.arefDirect(runtime, array, converter, i);
        }
        return context.nil;
    }

    @JRubyMethod(name = "+")
    public IRubyObject op_plus(ThreadContext context, IRubyObject other) {
        final Object array = getObject();
        if ( other instanceof ArrayJavaProxy ) {
            final Object otherArray = ((ArrayJavaProxy) other).getObject();
            final Class<?> componentType = array.getClass().getComponentType();
            if ( componentType.isAssignableFrom( otherArray.getClass().getComponentType() ) ) {
                return ArrayUtils.concatArraysDirect(context, array, otherArray);
            }
        }
        return ArrayUtils.concatArraysDirect(context, array, other);
    }

    @JRubyMethod
    public IRubyObject each(ThreadContext context, Block block) {
        final Ruby runtime = context.runtime;
        if ( ! block.isGiven() ) { // ... Enumerator.new(self, :each)
            return runtime.getEnumerator().callMethod("new", this, runtime.newSymbol("each"));
        }

        final Object array = getObject();
        final int length = Array.getLength(array);

        for ( int i = 0; i < length; i++ ) {
            IRubyObject element = ArrayUtils.arefDirect(runtime, array, converter, i);
            block.yield(context, element);
        }
        return this;
    }

    @JRubyMethod
    public IRubyObject each_with_index(final ThreadContext context, final Block block) {
        final Ruby runtime = context.runtime;
        if ( ! block.isGiven() ) { // ... Enumerator.new(self, :each)
            return runtime.getEnumerator().callMethod("new", this, runtime.newSymbol("each_with_index"));
        }

        final boolean arity2 = block.getSignature().arity() == Arity.TWO_ARGUMENTS;

        final Object array = getObject();
        final int length = Array.getLength(array);

        for ( int i = 0; i < length; i++ ) {
            IRubyObject element = ArrayUtils.arefDirect(runtime, array, converter, i);
            final RubyInteger index = RubyFixnum.newFixnum(runtime, i);
            if ( arity2 ) {
                block.yieldSpecific(context, element, index);
            } else {
                block.yield(context, RubyArray.newArray(runtime, element, index));
            }
        }
        return this;
    }

    @JRubyMethod(name = { "to_a", "entries" }, alias = "to_ary")
    public RubyArray to_a(ThreadContext context) {
        final Object array = getObject();
        return JavaUtil.convertJavaArrayToRubyWithNesting(context, array);
    }

    @JRubyMethod(name = "component_type")
    public IRubyObject component_type(ThreadContext context) {
        Class<?> componentType = getObject().getClass().getComponentType();
        final JavaClass javaClass = JavaClass.get(context.runtime, componentType);
        return Java.getProxyClass(context.runtime, javaClass);
    }

    @JRubyMethod
    public RubyString inspect(ThreadContext context) {
        return RubyString.newString(context.runtime, arrayToString());
    }

    @Override
    public String toString() {
        return arrayToString().toString();
    }

    private StringBuilder arrayToString() {
        final StringBuilder buffer = new StringBuilder(24);
        Class<?> componentClass = getObject().getClass().getComponentType();

        buffer.append(componentClass.getName());

        if (componentClass.isPrimitive()) {
            switch (componentClass.getName().charAt(0)) {
                case 'b':
                    if (componentClass == byte.class) buffer.append(Arrays.toString((byte[])getObject()));
                    else /* if (componentClass == boolean.class) */ buffer.append(Arrays.toString((boolean[])getObject()));
                    break;
                case 's':
                    /* if (componentClass == short.class) */ buffer.append(Arrays.toString((short[])getObject()));
                    break;
                case 'c':
                    /* if (componentClass == char.class) */ buffer.append(Arrays.toString((char[])getObject()));
                    break;
                case 'i':
                    /* if (componentClass == int.class) */ buffer.append(Arrays.toString((int[])getObject()));
                    break;
                case 'l':
                    /* if (componentClass == long.class) */ buffer.append(Arrays.toString((long[])getObject()));
                    break;
                case 'f':
                    /* if (componentClass == float.class) */ buffer.append(Arrays.toString((float[])getObject()));
                    break;
                case 'd':
                    /* if (componentClass == double.class) */ buffer.append(Arrays.toString((double[])getObject()));
                    break;
            }
        } else {
            buffer.append(Arrays.toString((Object[]) getObject()));
        }

        return buffer.append('@').append(Integer.toHexString(inspectHashCode()));
    }

    @Override
    @JRubyMethod(name = "==")
    public RubyBoolean op_equal(ThreadContext context, IRubyObject other) {
        if ( other instanceof RubyArray ) {
            // we respond_to? to_ary thus shall handle [1].to_java == [1]
            return context.runtime.newBoolean( equalsRubyArray((RubyArray) other) );
        }
        return eql_p(context, other);
    }

    private boolean equalsRubyArray(final RubyArray rubyArray) {
        final Object thisArray = this.getObject();
        final int len = rubyArray.size();
        if ( len != Array.getLength(thisArray) ) return false;
        final Class<?> componentType = thisArray.getClass().getComponentType();
        for ( int i = 0; i < len; i++ ) {
            final Object ruby = rubyArray.eltInternal(i).toJava(componentType);
            final Object elem = Array.get(thisArray, i);
            if ( ruby == null ) return elem == null;
            if ( ! ruby.equals(elem) ) return false;
        }
        return true;
    }

    @JRubyMethod(name = "eql?")
    public RubyBoolean eql_p(ThreadContext context, IRubyObject obj) {
        boolean equals = false;
        if ( obj instanceof ArrayJavaProxy ) {
            final ArrayJavaProxy that = (ArrayJavaProxy) obj;
            equals = arraysEquals(this.getObject(), that.getObject());
        }
        else if ( obj.getClass().isArray() ) {
            equals = arraysEquals(getObject(), obj);
        }
        return context.runtime.newBoolean(equals);
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj instanceof ArrayJavaProxy ) {
            final ArrayJavaProxy that = (ArrayJavaProxy) obj;
            final Object thisArray = this.getObject();
            final Object thatArray = that.getObject();
            return arraysEquals(thisArray, thatArray);
        }
        return false;
    }

    private static boolean arraysEquals(final Object thisArray, final Object thatArray) {
        final Class<?> componentType = thisArray.getClass().getComponentType();
        if ( ! componentType.equals(thatArray.getClass().getComponentType()) ) {
            return false;
        }
        if ( componentType.isPrimitive() ) {
            switch ( componentType.getName().charAt(0) ) {
                case 'b':
                    if (componentType == byte.class) return Arrays.equals((byte[]) thisArray, (byte[]) thatArray);
                    else /* if (componentType == boolean.class) */ return Arrays.equals((boolean[]) thisArray, (boolean[]) thatArray);
                case 's':
                    /* if (componentType == short.class) */ return Arrays.equals((short[]) thisArray, (short[]) thatArray);
                case 'c':
                    /* if (componentType == char.class) */ return Arrays.equals((char[]) thisArray, (char[]) thatArray);
                case 'i':
                    /* if (componentType == int.class) */ return Arrays.equals((int[]) thisArray, (int[]) thatArray);
                case 'l':
                    /* if (componentType == long.class) */ return Arrays.equals((long[]) thisArray, (long[]) thatArray);
                case 'f':
                    /* if (componentType == float.class) */ return Arrays.equals((float[]) thisArray, (float[]) thatArray);
                case 'd':
                    /* if (componentType == double.class) */ return Arrays.equals((double[]) thisArray, (double[]) thatArray);
            }
        }
        return Arrays.equals((Object[]) thisArray, (Object[]) thatArray);
    }

    @Override
    @JRubyMethod
    public RubyFixnum hash() {
        return getRuntime().newFixnum( hashCode() );
    }

    @Override
    public int hashCode() {
        final Object array = getObject();
        final Class<?> componentType = array.getClass().getComponentType();
        if ( componentType.isPrimitive() ) {
            switch ( componentType.getName().charAt(0) ) {
                case 'b':
                    if (componentType == byte.class) return 11 * Arrays.hashCode((byte[]) array);
                    else /* if (componentType == boolean.class) */ return 11 * Arrays.hashCode((boolean[]) array);
                case 's':
                    /* if (componentType == short.class) */ return 11 * Arrays.hashCode((short[]) array);
                case 'c':
                    /* if (componentType == char.class) */ return 11 * Arrays.hashCode((char[]) array);
                case 'i':
                    /* if (componentType == int.class) */ return 11 * Arrays.hashCode((int[]) array);
                case 'l':
                    /* if (componentType == long.class) */ return 11 * Arrays.hashCode((long[]) array);
                case 'f':
                    /* if (componentType == float.class) */ return 11 * Arrays.hashCode((float[]) array);
                case 'd':
                    /* if (componentType == double.class) */ return 11 * Arrays.hashCode((double[]) array);
            }
        }
        return 11 * Arrays.hashCode((Object[]) array);
    }

    @Override
    public IRubyObject dup() {
        final Ruby runtime = getRuntime();

        RubyObject dup = new ArrayJavaProxy(runtime, getMetaClass(), cloneObject(), converter);

        if (isTaint()) dup.setTaint(true);
        initCopy(dup, this, "initialize_dup");

        return dup;
    }

    private static void initCopy(IRubyObject clone, IRubyObject original, String method) {
        original.copySpecialInstanceVariables(clone);
        if (original.hasVariables()) clone.syncVariables(original);
    }

    @Override
    @JRubyMethod(name = "clone")
    public IRubyObject rbClone() {
        final Ruby runtime = getRuntime();

        RubyObject clone = new ArrayJavaProxy(runtime, getMetaClass(), cloneObject(), converter);
        clone.setMetaClass(getSingletonClassClone());

        if (isTaint()) clone.setTaint(true);
        initCopy(clone, this, "initialize_clone");
        if (isFrozen()) clone.setFrozen(true);

        return clone;
    }

    @Override
    protected Object cloneObject() {
        final Object array = getObject();
        final Class<?> componentType = array.getClass().getComponentType();
        if ( componentType.isPrimitive() ) {
            switch ( componentType.getName().charAt(0) ) {
                case 'b':
                    if (componentType == byte.class) return ((byte[]) array).clone();
                    else /* if (componentType == boolean.class) */ return ((boolean[]) array).clone();
                case 's':
                    /* if (componentType == short.class) */ return ((short[]) array).clone();
                case 'c':
                    /* if (componentType == char.class) */ return ((char[]) array).clone();
                case 'i':
                    /* if (componentType == int.class) */ return ((int[]) array).clone();
                case 'l':
                    /* if (componentType == long.class) */ return ((long[]) array).clone();
                case 'f':
                    /* if (componentType == float.class) */ return ((float[]) array).clone();
                case 'd':
                    /* if (componentType == double.class) */ return ((double[]) array).clone();
            }
        }
        return ((Object[]) array).clone();
    }

    public IRubyObject getRange(ThreadContext context, IRubyObject[] args) {
        if (args.length == 1) {
            return getRange(context, args[0]);
        }
        if (args.length == 2) {
            return getRange(context, args[0], args[1]);
        }
        throw context.runtime.newArgumentError(args.length, 1);
    }

    public IRubyObject getRange(ThreadContext context, IRubyObject arg0) {
        if ( arg0 instanceof RubyRange ) {
            return arrayRange(context, (RubyRange) arg0);
        }
        throw context.runtime.newTypeError(arg0, context.runtime.getRange());
    }

    private IRubyObject arrayRange(final ThreadContext context, final RubyRange range) {
        final Object array = getObject();
        final int arrayLength = Array.getLength( array );

        final IRubyObject rFirst = range.first(context);
        final IRubyObject rLast = range.last(context);
        if ( rFirst instanceof RubyFixnum && rLast instanceof RubyFixnum ) {
            int first = (int) ((RubyFixnum) rFirst).getLongValue();
            int last = (int) ((RubyFixnum) rLast).getLongValue();

            first = first >= 0 ? first : arrayLength + first;
            last = last >= 0 ? last : arrayLength + last;

            int newLength = last - first;
            if ( range.isExcludeEnd() ) newLength += 1;

            if ( newLength <= 0 ) {
                return ArrayUtils.emptyJavaArrayDirect(context, array.getClass().getComponentType());
            }

            return ArrayUtils.javaArraySubarrayDirect(context, array, first, newLength);
        }
        throw context.runtime.newTypeError("only Integer ranges supported");
    }

    public IRubyObject getRange(ThreadContext context, IRubyObject first, IRubyObject length) {
        return arrayRange(context, first, length);
    }

    private IRubyObject arrayRange(final ThreadContext context,
        final IRubyObject rFirst, final IRubyObject rLength) {
        final Object array = getObject();
        final int arrayLength = Array.getLength( array );

        if ( rFirst instanceof RubyFixnum && rLength instanceof RubyFixnum ) {
            int first = (int) ((RubyFixnum) rFirst).getLongValue();
            int length = (int) ((RubyFixnum) rLength).getLongValue();

            if ( length > arrayLength ) {
                throw context.runtime.newIndexError("length specifed is longer than array");
            }
            if ( length <= 0 ) {
                return ArrayUtils.emptyJavaArrayDirect(context, array.getClass().getComponentType());
            }

            first = first >= 0 ? first : arrayLength + first;
            return ArrayUtils.javaArraySubarrayDirect(context, array, first, length);
        }
        throw context.runtime.newTypeError("only Fixnum ranges supported");
    }

    private static final class ArrayNewMethod extends org.jruby.internal.runtime.methods.JavaMethod.JavaMethodOne {

        private final DynamicMethod newMethod;

        ArrayNewMethod(RubyModule implClass, Visibility visibility) {
            this(implClass, visibility, implClass.searchMethod("new"));
        }

        public ArrayNewMethod(RubyModule implClass, Visibility visibility, DynamicMethod oldNew) {
            super(implClass, visibility, "new");
            this.newMethod = oldNew;
        }

        @Override
        public final IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0) {
            final Ruby runtime = context.runtime;

            if ( ! ( arg0 instanceof JavaArray ) ) {
                throw runtime.newTypeError(arg0, runtime.getJavaSupport().getJavaArrayClass());
            }

            IRubyObject proxy = newMethod.call(context, self, clazz, "new_proxy");
            proxy.dataWrapStruct(arg0);
            return proxy;
        }

    }
}
