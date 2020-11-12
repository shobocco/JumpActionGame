package jp.techacademy.shirou.ogata.jumpactiongame

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.Vector2
import kotlin.math.*

class Enemy(cx:Float,cy:Float,texture: Texture, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int)
    : Sprite(texture, srcX, srcY, srcWidth, srcHeight) {

    companion object {
        // 横幅、高さ
        val ENEMY_WIDTH = 1.0f
        val ENEMY_HEIGHT = 1.0f

        // 状態
        val ENEMY_EXIST = 0
        val ENEMY_NONE = 1
    }

    var mState: Int = 0

    //Stepを中心座標として指定された半径で回転する

    var mRadius:Float = 3.0f

    var mAngle:Float = 0.0f
    var mCenterPoint:Vector2 = Vector2(0f,0f)



    init {
        setSize(ENEMY_WIDTH, ENEMY_HEIGHT)
        mState = ENEMY_EXIST

        mAngle = (0..359).random().toFloat()
        mCenterPoint.x = cx
        mCenterPoint.y = cy
        update(0f)

    }

    // 座標を更新する
    fun update(deltaTime: Float) {
       mAngle += 30.0f * deltaTime
        x = mCenterPoint.x + (cos(mAngle * PI / 180) * mRadius).toFloat()
        y = mCenterPoint.y + (sin(mAngle * PI / 180) * mRadius).toFloat()

    }

    fun hit() {
        mState = ENEMY_NONE
        setAlpha(0f)
    }
}