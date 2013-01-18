package gui

import swing.{ ComboBox, Orientation, BoxPanel }
import Orientation._
import swing.event.SelectionChanged
import reactive.{EventStream, EventStreamSourceProxy}
import java.awt.Component

/**
 * This class is used to display 3 combox boxes where the elements in the second box depend on the selected element in the first,
 * and the elements in the third depend on the second
 *
 * It is also an EventStream for (T, S, U) where T is the first selected element, S is the second element and U the third
 */
class DependentComboBox2[T, S, U](main: Seq[T], second: T => Seq[S], third: (T, S) => Seq[U], orientation: Orientation.Value = Horizontal) extends BoxPanel(orientation)
   with EventStreamSourceProxy[(T, S, U)] {
  override def self: Component with EventStream[(T, S, U)] = this.asInstanceOf[Component with EventStream[(T, S, U)]]

  // the first or "main" combobox
  lazy val firstComboBox = new ComboBox(main)
  // the dependent comboboxes need to be recreated because the ComboBox model is immutable
  var secondComboBox = new ComboBox(second(main.head))
  var thirdComboBox = new ComboBox(third(main.head, second(main.head).head))

  /**
   * update contents on startup
   */
  updateContents()

  private def updateContents() {
    contents.clear()
    contents += firstComboBox
    contents += secondComboBox
    contents += thirdComboBox
    listenTo(firstComboBox.selection)
    listenTo(secondComboBox.selection)
    listenTo(thirdComboBox.selection)
  }

  /**
   * when the first or the second combo box changes we must propagate the changes to the dependent comboboxes
   */
  reactions += {
    case SelectionChanged(_) => {
      val selectedFirst = firstComboBox.selection.item
      val selectedSecond = {
        val selectedSec = secondComboBox.selection.item
        if (second(selectedFirst).contains(selectedSec)) selectedSec
        else                                             second(selectedFirst).head
      }
      val selectedThird = {
        val selectedTh = thirdComboBox.selection.item
        if (third(selectedFirst, selectedSecond).contains(selectedTh)) selectedTh
        else                                            third(selectedFirst, selectedSecond).head
      }
      source.fire((selectedFirst, selectedSecond, selectedThird))

      secondComboBox = new ComboBox(second(firstComboBox.selection.item))
      secondComboBox.selection.item = selectedSecond

      thirdComboBox = new ComboBox(third(selectedFirst, secondComboBox.selection.item))
      thirdComboBox.selection.item = selectedThird

      updateContents()
    }
  }

  /** Testing functions */
  def selectFirst(t: T): this.type = {
    firstComboBox.selection.item = t
    this
  }

  def selectSecond(s: S) = {
    secondComboBox.selection.item = s
    this
  }

  def selectThird(u: U) = {
    thirdComboBox.selection.item = u
    this
  }


}