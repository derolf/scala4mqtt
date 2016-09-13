import scala.reflect.ClassTag

/**
 * Created by daniel on 9/7/15.
 */
package object home {
    def isSubTypeOf[Parent: ClassTag](sub: ClassTag[_]) = implicitly[ClassTag[Parent]].runtimeClass.isAssignableFrom(sub.runtimeClass)
}
