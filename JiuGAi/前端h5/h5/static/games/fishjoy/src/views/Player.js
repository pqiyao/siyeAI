
(function(){

var ns = Q.use("fish"), game = ns.game;

var Player = ns.Player = function(props)
{
	this.id = null;
	this.coin = 0;
	this.numCapturedFishes = 0;
	
	this.cannon = null;
	this.cannonMinus = null;
	this.cannonPlus = null;
	this.coinNum = null;
	
	props = props || {};
	Q.merge(this, props, true);
	
	this.init();
};

Player.prototype.init = function()
{
	var me = this, power = 1;
	var scale = game.uiScale || 1;
	
	this.cannon = new ns.Cannon(ns.R.cannonTypes[power]);
	this.cannon.id = "cannon";
	this.cannon.scaleX = this.cannon.scaleY = scale;
	this.cannon.x = game.bottom.x + 425 * scale;
	this.cannon.y = game.height - 10 * scale;
	
	this.cannonMinus = new Q.Button(ns.R.cannonMinus);
	this.cannonMinus.id = "cannonMinus";
	this.cannonMinus.scaleX = this.cannonMinus.scaleY = scale;
	this.cannonMinus.x = game.bottom.x + 340 * scale;
	this.cannonMinus.y = game.bottom.y + 36 * scale;
	this.cannonMinus.onEvent = function(e)
	{
		if(e.type == game.events[1])
		{
			me.cannon.setPower(-1, true);
		}
	};
	
	this.cannonPlus = new Q.Button(ns.R.cannonPlus);
	this.cannonPlus.id = "cannonPlus";
	this.cannonPlus.scaleX = this.cannonPlus.scaleY = scale;
	this.cannonPlus.x = this.cannonMinus.x + 140 * scale;
	this.cannonPlus.y = this.cannonMinus.y;
	this.cannonPlus.onEvent = function(e)
	{
		if(e.type == game.events[1])
		{
			me.cannon.setPower(1, true);
		}
	};
	
	this.coinNum = new ns.Num({id:"coinNum", src:ns.R.numBlack, max:6, gap:3, autoAddZero:true});
	this.coinNum.scaleX = this.coinNum.scaleY = scale;
	this.coinNum.x = game.bottom.x + 20 * scale;
	this.coinNum.y = game.bottom.y + 44 * scale;
	this.updateCoin(this.coin);
	
	game.stage.addChild(this.cannon, this.cannonMinus, this.cannonPlus, this.coinNum);
};

Player.prototype.fire = function(targetPoint)
{	
	var cannon = this.cannon, power = cannon.power, speed = 5;
	if(this.coin < power) return;
	
	//cannon fire
	var dir = ns.Utils.calcDirection(cannon, targetPoint), degree = dir.degree;
	if(degree == -90) degree = 0;
	else if(degree < 0 && degree > -90) degree = -degree;
	else if(degree >= 180 && degree <= 270) degree = 180 - degree;
	cannon.fire(degree);
	
	//fire a bullet
	var sin = Math.sin(degree*Q.DEG_TO_RAD), cos = Math.cos(degree*Q.DEG_TO_RAD);
	var bullet = new ns.Bullet(ns.R.bullets[power - 1]);
	bullet.x = cannon.x + (cannon.regY + 20) * sin;
	bullet.y = cannon.y - (cannon.regY + 20) * cos;
	bullet.rotation = degree;
	bullet.power = power;
	bullet.speedX = speed * sin;
	bullet.speedY = speed * cos;
	game.stage.addChild(bullet);
	
	//deduct coin
	this.updateCoin(-power, true);
}

Player.prototype.captureFish = function(fish)
{
	this.updateCoin(fish.coin, true);
	this.numCapturedFishes++;
};

Player.prototype.updateCoin = function(coin, increase)
{
	if(increase) this.coin += coin;
	else this.coin = coin;
	if(this.coin > 999999) this.coin = 999999;
	this.coinNum.setValue(this.coin);
};

})();
