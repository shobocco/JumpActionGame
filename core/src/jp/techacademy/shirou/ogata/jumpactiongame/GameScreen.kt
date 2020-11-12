package jp.techacademy.shirou.ogata.jumpactiongame
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport
import java.util.*


class GameScreen(private val mGame: JumpActionGame) : ScreenAdapter() {
    companion object {
        //縦横15:10
        val CAMERA_WIDTH = 10f
        val CAMERA_HEIGHT = 15f
        val WORLD_WIDTH = 10f
        val WORLD_HEIGHT = 15 * 20
        val GUI_WIDTH = 320f
        val GUI_HEIGHT = 480f

        val GAME_STATE_READY = 0
        val GAME_STATE_PLAYING = 1
        val GAME_STATE_GAMEOVER = 2

        // 重力
        val GRAVITY = -12
    }

    private val mBg: Sprite
    private val mCamera: OrthographicCamera
    private val mGuiCamera: OrthographicCamera
    private val mViewPort: FitViewport
    private val mGuiViewPort: FitViewport

    private var mRandom: Random
    private var mSteps: ArrayList<Step>
    private var mStars: ArrayList<Star>
    private var mEnemies: ArrayList<Enemy>
    private lateinit var mUfo: Ufo
    private lateinit var mPlayer: Player
    private var mSoundNg:Sound
    private var mSoundGet:Sound

    private var mGameState: Int
    private var mHeightSoFar: Float = 0f
    private var mTouchPoint: Vector3
    private var mFont: BitmapFont
    private var mScore: Int
    private var mHighScore: Int
    private var mPrefs: Preferences // ←追加する


    init {
        // 背景の準備
        val bgTexture = Texture("back.png")
        // TextureRegionで切り出す時の原点は左上
        mBg = Sprite(TextureRegion(bgTexture, 0, 0, 540, 810))
        mBg.setSize(CAMERA_WIDTH, CAMERA_HEIGHT)
        mBg.setPosition(0f, 0f)

        // カメラ、ViewPortを生成、設定する
        mCamera = OrthographicCamera()
        mCamera.setToOrtho(false, CAMERA_WIDTH, CAMERA_HEIGHT)
        mViewPort = FitViewport(CAMERA_WIDTH, CAMERA_HEIGHT, mCamera)

        // GUI用のカメラを設定する
        mGuiCamera = OrthographicCamera()
        mGuiCamera.setToOrtho(false, GUI_WIDTH, GUI_HEIGHT)
        mGuiViewPort = FitViewport(GUI_WIDTH, GUI_HEIGHT, mGuiCamera)

        // プロパティの初期化
        mRandom = Random()
        mSteps = ArrayList<Step>()
        mStars = ArrayList<Star>()
        mEnemies = ArrayList<Enemy>()
        mGameState = GAME_STATE_READY
        mTouchPoint = Vector3()

        mFont = BitmapFont(Gdx.files.internal("font.fnt"), Gdx.files.internal("font.png"), false)
        mFont.data.setScale(0.8f)
        mScore = 0
        mHighScore = 0

        mSoundNg = Gdx.audio.newSound(Gdx.files.internal("ng.mp3"))
        mSoundGet = Gdx.audio.newSound(Gdx.files.internal("get.mp3"))

        // ハイスコアをPreferencesから取得する
        mPrefs = Gdx.app.getPreferences("jp.techacademy.shirou.ogata.jumpactiongame") // ←追加する
        mHighScore = mPrefs.getInteger("HIGHSCORE", 0) // ←追加する

        createStage()
    }

    override fun render(delta: Float) {
        // それぞれの状態をアップデートする
        update(delta)

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // カメラの中心を超えたらカメラを上に移動させる つまりキャラが画面の上半分には絶対に行かない
        if (mPlayer.y > mCamera.position.y) {
            mCamera.position.y = mPlayer.y
        }

        // カメラの座標をアップデート（計算）し、スプライトの表示に反映させる
        mCamera.update()
        mGame.batch.projectionMatrix = mCamera.combined

        mGame.batch.begin()

        // 背景
        // 原点は左下
        mBg.setPosition(mCamera.position.x - CAMERA_WIDTH / 2, mCamera.position.y - CAMERA_HEIGHT / 2)
        mBg.draw(mGame.batch)

        // Step
        for (i in 0 until mSteps.size) {
            mSteps[i].draw(mGame.batch)
        }

        // Star
        for (i in 0 until mStars.size) {
            mStars[i].draw(mGame.batch)
        }

        // Enemy
        for (i in 0 until mEnemies.size) {
            mEnemies[i].draw(mGame.batch)
        }

        // UFO
        mUfo.draw(mGame.batch)

        //Player
        mPlayer.draw(mGame.batch)

        mGame.batch.end()

        // スコア表示
        mGuiCamera.update() // ←追加する
        mGame.batch.projectionMatrix = mGuiCamera.combined  // ←追加する
        mGame.batch.begin() // ←追加する
        mFont.draw(mGame.batch, "HighScore: $mHighScore", 16f, GUI_HEIGHT - 15) // ←追加する
        mFont.draw(mGame.batch, "Score: $mScore", 16f, GUI_HEIGHT - 35)    // ←追加する
        mGame.batch.end()   // ←追加する
    }

    override fun resize(width: Int, height: Int) {
        mViewPort.update(width, height)
        mGuiViewPort.update(width, height)
    }

    // ステージを作成する
    private fun createStage() {

        // テクスチャの準備
        val stepTexture = Texture("step.png")
        val starTexture = Texture("star.png")
        val playerTexture = Texture("uma.png")
        val ufoTexture = Texture("ufo.png")
        val enemyTexture = Texture("monster06.png")

        // StepとStarをゴールの高さまで配置していく
        var y = 0f

        //ジャンプの最高点はvy^2/2g ≒ 5
        val maxJumpHeight = Player.PLAYER_JUMP_VELOCITY * Player.PLAYER_JUMP_VELOCITY / (2 * -GRAVITY)
        while (y < WORLD_HEIGHT - 5) {

            //20%で動くタイル
            val type = if(mRandom.nextFloat() > 0.8f) Step.STEP_TYPE_MOVING else Step.STEP_TYPE_STATIC

            //位置を決定
            val x = mRandom.nextFloat() * (WORLD_WIDTH - Step.STEP_WIDTH)

            //配置
            val step = Step(type, stepTexture, 0, 0, 144, 36)
            step.setPosition(x, y)
            mSteps.add(step)

            //30%で敵配置

            if (mRandom.nextFloat() > 0.7f && mSteps.size > 1) {
                val enemy = Enemy(step.x + Step.STEP_WIDTH / 2,step.y, enemyTexture,0,0,160,160)
                mEnemies.add(enemy)
            }


            //40%で星配置
            if (mRandom.nextFloat() > 0.6f) {
                val star = Star(starTexture, 0, 0, 72, 72)
                star.setPosition(step.x + mRandom.nextFloat(), step.y + Star.STAR_HEIGHT + mRandom.nextFloat() * 3)
                mStars.add(star)
            }

            y += (maxJumpHeight - 0.5f)
            y -= mRandom.nextFloat() * (maxJumpHeight / 3)
        }

        // Playerを配置
        mPlayer = Player(playerTexture, 0, 0, 72, 72)
        mPlayer.setPosition(WORLD_WIDTH / 2 - mPlayer.width / 2, Step.STEP_HEIGHT)

        // ゴールのUFOを配置（真ん中）
        mUfo = Ufo(ufoTexture, 0, 0, 120, 74)
        mUfo.setPosition(WORLD_WIDTH / 2 - Ufo.UFO_WIDTH / 2, y)
    }

    // それぞれのオブジェクトの状態をアップデートする
    private fun update(delta: Float) {
        when (mGameState) {
            GAME_STATE_READY ->
                updateReady()
            GAME_STATE_PLAYING ->
                updatePlaying(delta)
            GAME_STATE_GAMEOVER ->
                updateGameOver()
        }
    }

    private fun updateReady() {
        //画面がタップされたらメイン画面に進む
        if (Gdx.input.justTouched()) {
            mGameState = GAME_STATE_PLAYING
        }
    }

    //各種オブジェクトの状態更新
    //deltaは前フレームとの時間差（カリキュラムに書いておいてほしい）
    private fun updatePlaying(delta: Float) {
        var accel = 0f
        if (Gdx.input.isTouched) {
            //タッチ座標をViewPort座標に変換
            mGuiViewPort.unproject(mTouchPoint.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))
            val left = Rectangle(0f, 0f, GUI_WIDTH / 2, GUI_HEIGHT)
            val right = Rectangle(GUI_WIDTH / 2, 0f, GUI_WIDTH / 2, GUI_HEIGHT)
            //左右の矩形をセットしてどっちに含まれるか判定
            //（左右だけならx判定だけでいいような……？）
            if (left.contains(mTouchPoint.x, mTouchPoint.y)) {
                accel = 5.0f
            }
            if (right.contains(mTouchPoint.x, mTouchPoint.y)) {
                accel = -5.0f
            }
        }

        // 足場の情報更新
        for (i in 0 until mSteps.size) {
            mSteps[i].update(delta)
        }

        // 足場の情報更新
        for (i in 0 until mEnemies.size) {
            mEnemies[i].update(delta)
        }


        // Player
        if (mPlayer.y <= 0.5f) {
            mPlayer.hitStep()
        }
        mPlayer.update(delta, accel)
        mHeightSoFar = Math.max(mPlayer.y, mHeightSoFar)

        // 当たり判定を行う
        checkCollision()

        // ゲームオーバーか判断する
        checkGameOver()
    }

    private fun checkGameOver() {
        if (mHeightSoFar - CAMERA_HEIGHT / 2 > mPlayer.y) {
            Gdx.app.log("JampActionGame", "GAMEOVER")
            mGameState = GAME_STATE_GAMEOVER
        }
        for ( i in 0 until mEnemies.size) {
            val enemy = mEnemies[i]
            if(mPlayer.boundingRectangle.overlaps(enemy.boundingRectangle)){
                Gdx.app.log("JampActionGame", "GAMEOVER")
                mGameState = GAME_STATE_GAMEOVER
                mSoundNg.play(1.0f);
            }
        }
    }
    private fun updateGameOver() {
        if (Gdx.input.justTouched()) {
            mGame.screen = ResultScreen(mGame, mScore)
        }
    }

    private fun checkCollision() {

        //overlapsだけでRectangle同士の当たり判定をとれる。便利！

        // UFO(ゴールとの当たり判定)
        if (mPlayer.boundingRectangle.overlaps(mUfo.boundingRectangle)) {
            mGameState = GAME_STATE_GAMEOVER
            return
        }




        // Starとの当たり判定
        for (i in 0 until mStars.size) {
            val star = mStars[i]

            if (star.mState == Star.STAR_NONE) {
                continue
            }

            if (mPlayer.boundingRectangle.overlaps(star.boundingRectangle)) {
                star.get()
                mSoundGet.play(1f)
                mScore++    // ←追加する
                if (mScore > mHighScore) {  // ←追加する
                    mHighScore = mScore     // ←追加する
                    // ←追加する
                    //ハイスコアをPreferenceに保存する
                    mPrefs.putInteger("HIGHSCORE", mHighScore)  // ←追加する
                    mPrefs.flush()  // ←追加する
                }
                break
            }
        }

        // Stepとの当たり判定
        // 上昇中はStepとの当たり判定を確認しない
        if (mPlayer.velocity.y > 0) {
            return
        }

        for (i in 0 until mSteps.size) {
            val step = mSteps[i]

            if (step.mState == Step.STEP_STATE_VANISH) {
                continue
            }

            if (mPlayer.y > step.y) {
                if (mPlayer.boundingRectangle.overlaps(step.boundingRectangle)) {
                    mPlayer.hitStep()
                    if (mRandom.nextFloat() > 0.5f) {
                        step.vanish()
                    }
                    break
                }
            }
        }
    }
}