/*
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2014 devemux86
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.android.graphics;

import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.Display;
import org.mapsforge.core.graphics.Matrix;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Position;
import org.mapsforge.core.mapelements.PointTextContainer;
import org.mapsforge.core.mapelements.SymbolContainer;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.model.Rotation;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

public class AndroidPointTextContainer extends PointTextContainer {

	private StaticLayout frontLayout;
	private StaticLayout backLayout;
	private float boxWidth;
	private float boxHeight;

	AndroidPointTextContainer(Point xy, double horizontalOffset, double verticalOffset,
	                          Display display, int priority, String text, Paint paintFront, Paint paintBack,
	                          SymbolContainer symbolContainer, Position position, int maxTextWidth) {
		super(xy, horizontalOffset, verticalOffset, display, priority, text,
				paintFront, paintBack, symbolContainer, position, maxTextWidth);

		if (this.textWidth > this.maxTextWidth) {

			// if the text is too wide its layout is done by the Android StaticLayout class,
			// which automagically inserts line breaks. There is not a whole lot of useful
			// documentation of this class.
			// For below and above placements the text is center-aligned, for left on the right
			// and for right on the left.
			// One disadvantage is that it will always keep the text within the maxWidth,
			// even if that means breaking text mid-word.
			// This code currently does not play that well with the LabelPlacement algorithm.
			// The best way to disable it is to make the maxWidth really wide.

			TextPaint frontTextPaint = new TextPaint(AndroidGraphicFactory.getPaint(this.paintFront));
			TextPaint backTextPaint = null;
			if (this.paintBack != null) {
				backTextPaint = new TextPaint(AndroidGraphicFactory.getPaint(this.paintBack));
			}

			Layout.Alignment alignment = Layout.Alignment.ALIGN_CENTER;
			if (Position.LEFT == this.position
					|| Position.BELOW_LEFT == this.position
					|| Position.ABOVE_LEFT == this.position) {
				alignment = Layout.Alignment.ALIGN_OPPOSITE;
			} else if (Position.RIGHT == this.position
					|| Position.BELOW_RIGHT == this.position
					|| Position.ABOVE_RIGHT == this.position) {
				alignment = Layout.Alignment.ALIGN_NORMAL;
			}

			// strange Android behaviour: if alignment is set to center, then
			// text is rendered with right alignment if using StaticLayout
			frontTextPaint.setTextAlign(android.graphics.Paint.Align.LEFT);
			backTextPaint.setTextAlign(android.graphics.Paint.Align.LEFT);

			frontLayout = new StaticLayout(this.text, frontTextPaint, this.maxTextWidth, alignment, 1, 0, false);
			backLayout = null;
			if (this.paintBack != null) {
				backLayout = new StaticLayout(this.text, backTextPaint, this.maxTextWidth, alignment, 1, 0, false);
			}

			this.boxWidth = frontLayout.getWidth();
			this.boxHeight = frontLayout.getHeight();

		} else {
			this.boxWidth = textWidth;
			this.boxHeight = textHeight;
		}

		// TODO Rotation: this will need to be recalculated depending on rotation angle.
		switch (this.position) {
			case CENTER:
				boundary = new Rectangle(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f);
				break;
			case BELOW:
				boundary = new Rectangle(-boxWidth / 2f, 0, boxWidth / 2f, boxHeight);
				break;
			case BELOW_LEFT:
				boundary = new Rectangle(-boxWidth, 0, 0, boxHeight);
				break;
			case BELOW_RIGHT:
				boundary = new Rectangle(0, 0, boxWidth, boxHeight);
				break;
			case ABOVE:
				boundary = new Rectangle(-boxWidth / 2f, -boxHeight, boxWidth / 2f, 0);
				break;
			case ABOVE_LEFT:
				boundary = new Rectangle(-boxWidth, -boxHeight, 0, 0);
				break;
			case ABOVE_RIGHT:
				boundary = new Rectangle(0, -boxHeight, boxWidth, 0);
				break;
			case LEFT:
				boundary = new Rectangle(-boxWidth, -boxHeight / 2f, 0, boxHeight / 2f);
				break;
			case RIGHT:
				boundary = new Rectangle(0, -boxHeight / 2f, boxWidth, boxHeight / 2f);
				break;
			default:
				break;
		}
	}

	@Override
	public void draw(Canvas canvas, Point origin, Matrix matrix, final Rotation rotation) {
		if (!this.isVisible) {
			return;
		}

		android.graphics.Canvas androidCanvas = AndroidGraphicFactory.getCanvas(canvas);

		if (this.textWidth > this.maxTextWidth) {
			// in this case we draw the precomputed staticLayout onto the canvas by translating
			// the canvas.
			androidCanvas.save();
			double x = this.xy.x - origin.x;
			double y = this.xy.y - origin.y;
			if (!Rotation.noRotation(rotation)) {
				androidCanvas.rotate(-rotation.degrees, rotation.px, rotation.py);
				Point rotated = rotation.rotate(x, y, true);
				x = rotated.x;
				y = rotated.y;
			}
			x += boundary.left + this.horizontalOffset;
			y += boundary.top + this.verticalOffset;

			androidCanvas.translate((float) x, (float) y);

			if (this.backLayout != null) {
				this.backLayout.draw(androidCanvas);
			}
			this.frontLayout.draw(androidCanvas);
			androidCanvas.restore();
		} else {
			// the origin of the text is the base line, so we need to make adjustments
			// so that the text will be within its box
			float textOffset = 0;
			switch (this.position) {
				case CENTER:
				case LEFT:
				case RIGHT:
					textOffset = textHeight / 2f;
					break;
				case BELOW:
				case BELOW_LEFT:
				case BELOW_RIGHT:
					textOffset = textHeight;
					break;
				default:
					break;
			}

			double x = (this.xy.x - origin.x);
			double y = (this.xy.y - origin.y);
			if (!Rotation.noRotation(rotation)) {
				androidCanvas.save();
				androidCanvas.rotate(-rotation.degrees, rotation.px, rotation.py);
				Point rotated = rotation.rotate(x, y, true);
				x = rotated.x;
				y = rotated.y;
			}

			// the offsets can only be applied after rotation, because the label needs to be rotated
			// around its center.
			x += this.horizontalOffset;
			y += this.verticalOffset + textOffset; // the text offset has nothing to do with rotation, so add later

			if (this.paintBack != null) {
				androidCanvas.drawText(this.text, (float) x, (float) y, AndroidGraphicFactory.getPaint(this.paintBack));
			}
			androidCanvas.drawText(this.text, (float) x, (float) y, AndroidGraphicFactory.getPaint(this.paintFront));
			if (!Rotation.noRotation(rotation)) {
				androidCanvas.restore();
			}
		}
	}
}
